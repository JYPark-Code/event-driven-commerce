package com.jypark.tps1000.backoffice.settlement;

import com.jypark.tps1000.product.ProductRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

/**
 * 월별 정산 잡 (축 3 — Spring Batch).
 *
 * 구조: clearStep(해당 월 기존 정산 삭제) → aggregateStep(orders 집계 → settlements 적재).
 * 같은 달 재실행 = 덮어쓰기(delete 후 재집계)라 잡 전체가 멱등.
 *
 * 집계는 DB(GROUP BY)에 맡긴다 — 주문 수백만 행을 애플리케이션으로 끌어와 합산하는 대신,
 * 리더가 받는 행 수를 "상품 수"로 줄인다. 청크 크기 등 결정 근거는 docs/decisions.md 14번.
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
                                        JdbcPagingItemReader<ProductSales> monthlyOrderAggregateReader,
                                        ItemProcessor<ProductSales, Settlement> settlementItemProcessor,
                                        RepositoryItemWriter<Settlement> settlementWriter) {
        return new StepBuilder("aggregateSettlementStep", jobRepository)
                .<ProductSales, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyOrderAggregateReader)
                .processor(settlementItemProcessor)
                .writer(settlementWriter)
                .build();
    }

    /**
     * 해당 월 주문을 상품별로 GROUP BY 집계해 페이징으로 읽는다 (sort key = product_id).
     * FAILED 주문만 제외 — CREATED는 접수된 유효 주문이므로 포함 (decisions.md 14번).
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<ProductSales> monthlyOrderAggregateReader(
            DataSource dataSource,
            @Value("#{jobParameters['month']}") String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("product_id, SUM(quantity) AS total_quantity");
        queryProvider.setFromClause("FROM orders");
        queryProvider.setWhereClause("WHERE created_at >= :start AND created_at < :end AND status <> 'FAILED'");
        queryProvider.setGroupClause("product_id");
        queryProvider.setSortKeys(Map.of("product_id", org.springframework.batch.item.database.Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<ProductSales>()
                .name("monthlyOrderAggregateReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("start", start, "end", end))
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, rowNum) ->
                        new ProductSales(rs.getLong("product_id"), rs.getLong("total_quantity")))
                .build();
    }

    /** 수량 합에 정산 시점 단가를 곱해 정산 행으로 변환. 상품이 없으면(삭제 등) null 반환 → 스킵. */
    @Bean
    @StepScope
    public ItemProcessor<ProductSales, Settlement> settlementItemProcessor(
            ProductRepository productRepository,
            @Value("#{jobParameters['month']}") String month) {
        return sales -> productRepository.findById(sales.productId())
                .map(product -> Settlement.of(month, product.getId(), product.getName(),
                        sales.totalQuantity(), product.getPrice() * sales.totalQuantity()))
                .orElse(null);
    }

    @Bean
    public RepositoryItemWriter<Settlement> settlementWriter(SettlementRepository settlementRepository) {
        return new RepositoryItemWriterBuilder<Settlement>()
                .repository(settlementRepository)
                .build();
    }
}
