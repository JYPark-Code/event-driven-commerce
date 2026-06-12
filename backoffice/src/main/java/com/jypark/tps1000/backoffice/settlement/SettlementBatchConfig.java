package com.jypark.tps1000.backoffice.settlement;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

/**
 * 월별 정산 잡 (축 3 — Spring Batch).
 *
 * 구조: clearStep(해당 월 기존 정산 삭제) → aggregateStep(orders 집계 → settlements 적재).
 * 같은 달 재실행 = 덮어쓰기(delete 후 재집계)라 잡 전체가 멱등.
 *
 * 집계는 데이터 소유자인 order-service가 DB GROUP BY로 수행하고(내부 API), 리더는 그 결과를
 * 페이지 단위로 받는다 — 입력 행 수는 "상품 수" 규모 그대로 (청크 근거는 docs/decisions.md 14번).
 *
 * 다른 도메인 데이터 접근 경로 (MSA 2단계·3b — 직접 의존 해소):
 *  - 주문 집계: order-service 내부 API 호출 (월 1회 호출 패턴 — decisions.md 20번)
 *  - 상품 정보: 이벤트로 복제된 로컬 ProductReplica (건별 다회 조회 패턴 — decisions.md 18번)
 */
@Configuration
public class SettlementBatchConfig {

    public static final String JOB_NAME = "monthlySettlementJob";
    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job monthlySettlementJob(JobRepository jobRepository,
                                    @Qualifier("clearSettlementStep") Step clearSettlementStep,
                                    @Qualifier("aggregateSettlementStep") Step aggregateSettlementStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(clearSettlementStep)
                .next(aggregateSettlementStep)
                .build();
    }

    /** 재실행 멱등성: 해당 월의 기존 정산 행을 지우고 다시 집계한다. */
    @Bean
    public Step clearSettlementStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    SettlementRepository settlementRepository) {
        Tasklet clearTasklet = (contribution, chunkContext) -> {
            String month = (String) chunkContext.getStepContext().getJobParameters().get("month");
            settlementRepository.deleteBySettlementMonth(month);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("clearSettlementStep", jobRepository)
                .tasklet(clearTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step aggregateSettlementStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        ItemReader<ProductSales> monthlyOrderSalesReader,
                                        ItemProcessor<ProductSales, Settlement> settlementItemProcessor,
                                        RepositoryItemWriter<Settlement> settlementWriter) {
        return new StepBuilder("aggregateSettlementStep", jobRepository)
                .<ProductSales, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyOrderSalesReader)
                .processor(settlementItemProcessor)
                .writer(settlementWriter)
                .build();
    }

    /**
     * order-service 내부 API에서 해당 월 상품별 집계를 페이지 단위로 읽는다 (MSA 3b — FROM orders 직접 SQL 대체).
     * FAILED 제외 기준은 데이터 소유자(order-service)가 강제한다.
     */
    @Bean
    @StepScope
    public ItemReader<ProductSales> monthlyOrderSalesReader(
            @Value("#{jobParameters['month']}") String month,
            @Value("${backoffice.order-service.url}") String orderServiceUrl,
            @Value("${internal.api-token}") String internalApiToken) {
        // 내부 API 공유 시크릿(decisions.md 23번). 헤더 이름은 order 쪽 InternalApiTokenFilter와
        // 문자열로만 일치 — 상수 공유도 컴파일 의존이라 두지 않는다(18번 원칙).
        RestClient restClient = RestClient.builder()
                .baseUrl(orderServiceUrl)
                .defaultHeader("X-Internal-Token", internalApiToken)
                .build();
        return new MonthlyOrderSalesReader(restClient, month);
    }

    /** 수량 합에 정산 시점 단가(복제본)를 곱해 정산 행으로 변환. 복제본에 없으면(이벤트 미수신·삭제) null 반환 → 스킵. */
    @Bean
    @StepScope
    public ItemProcessor<ProductSales, Settlement> settlementItemProcessor(
            ProductReplicaRepository replicaRepository,
            @Value("#{jobParameters['month']}") String month) {
        return sales -> replicaRepository.findById(sales.productId())
                .map(replica -> Settlement.of(month, replica.getProductId(), replica.getName(),
                        sales.totalQuantity(), replica.getPrice() * sales.totalQuantity()))
                .orElse(null);
    }

    @Bean
    public RepositoryItemWriter<Settlement> settlementWriter(SettlementRepository settlementRepository) {
        return new RepositoryItemWriterBuilder<Settlement>()
                .repository(settlementRepository)
                .build();
    }
}
