package com.jypark.tps1000.order;

import com.jypark.tps1000.order.dto.MonthlyProductSalesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 서비스 간 내부 API (MSA 3b 선행 과제 — 정산의 orders 직접 SQL 집계 해소).
 * 정산(backoffice)이 월 1회 페이지 단위로 호출한다 — 호출 패턴이 "월별 집계"라
 * 이벤트 복제(상품, decisions.md 18번) 대신 동기 API를 택했다 (decisions.md 20번).
 *
 * /internal 네임스페이스: 외부 공개 API가 아님을 경로로 표시. 게이트웨이 비라우팅(404)에 더해
 * X-Internal-Token 공유 시크릿으로 보호된다(InternalApiTokenFilter, decisions.md 23번).
 * 운영이면 내부망 격리/mTLS로 올라갈 지점.
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderRepository orderRepository;

    @GetMapping("/monthly-sales")
    public List<MonthlyProductSalesResponse> monthlySales(@RequestParam String month,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "100") int size) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be yyyy-MM");
        }
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        return orderRepository.aggregateMonthlySales(start, end, OrderStatus.FAILED, PageRequest.of(page, size));
    }
}
