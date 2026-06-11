package com.jypark.tps1000.backoffice.settlement;

import org.springframework.batch.item.ItemReader;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * order-service의 월별 집계 내부 API를 페이지 단위로 읽는 정산 리더 (MSA 3b 선행 과제).
 * 기존 JdbcPagingItemReader(FROM orders 직접 SQL)를 대체 — DB가 분리되어도 동작한다.
 *
 * 응답은 backoffice가 소유한 ProductSales로 역직렬화한다 — order 모듈 클래스 공유 없음(JSON 계약).
 *
 * 재시작 안전성: JdbcPagingItemReader와 달리 ExecutionContext에 진행 상태를 저장하지 않는다 —
 * 정산 잡은 clear 스텝 덕에 처음부터 재실행이 멱등이라(decisions.md 14번) 중간 재개가 불필요.
 */
public class MonthlyOrderSalesReader implements ItemReader<ProductSales> {

    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String month;

    private List<ProductSales> page = List.of();
    private int index = 0;
    private int nextPageNumber = 0;
    private boolean lastPageSeen = false;

    public MonthlyOrderSalesReader(RestClient restClient, String month) {
        this.restClient = restClient;
        this.month = month;
    }

    @Override
    public ProductSales read() {
        if (index >= page.size()) {
            if (lastPageSeen) {
                return null;
            }
            page = fetchPage(nextPageNumber++);
            index = 0;
            if (page.size() < PAGE_SIZE) {
                lastPageSeen = true;
            }
            if (page.isEmpty()) {
                return null;
            }
        }
        return page.get(index++);
    }

    private List<ProductSales> fetchPage(int pageNumber) {
        return restClient.get()
                .uri("/internal/orders/monthly-sales?month={month}&page={page}&size={size}",
                        month, pageNumber, PAGE_SIZE)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
