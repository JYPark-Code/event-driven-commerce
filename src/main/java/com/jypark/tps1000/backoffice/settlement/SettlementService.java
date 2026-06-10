package com.jypark.tps1000.backoffice.settlement;

import com.jypark.tps1000.backoffice.settlement.dto.SettlementResponse;
import com.jypark.tps1000.backoffice.settlement.dto.SettlementRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final JobLauncher jobLauncher;
    private final Job monthlySettlementJob;
    private final SettlementRepository settlementRepository;

    /**
     * 정산 잡 동기 실행 (기본 JobLauncher = SyncTaskExecutor).
     * requestedAt 파라미터로 매 요청이 새 JobInstance — 같은 달 재정산 허용 (멱등성은 잡의 clear 스텝이 보장).
     * 데모라 동기로 충분; 정산이 분 단위로 길어지면 비동기 런처 + 폴링으로 전환할 지점.
     */
    public SettlementRunResponse runSettlement(String month) {
        validateMonth(month);
        JobParameters params = new JobParametersBuilder()
                .addString("month", month)
                .addLong("requestedAt", System.currentTimeMillis())
                .toJobParameters();
        try {
            JobExecution execution = jobLauncher.run(monthlySettlementJob, params);
            long settledProducts = settlementRepository.countBySettlementMonth(month);
            return new SettlementRunResponse(month, execution.getStatus().name(), settledProducts);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "settlement job failed", e);
        }
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlements(String month) {
        validateMonth(month);
        return settlementRepository.findBySettlementMonthOrderByProductId(month).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    private void validateMonth(String month) {
        try {
            YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be yyyy-MM");
        }
    }
}
