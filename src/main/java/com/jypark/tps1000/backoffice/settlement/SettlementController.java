package com.jypark.tps1000.backoffice.settlement;

import com.jypark.tps1000.backoffice.settlement.dto.SettlementResponse;
import com.jypark.tps1000.backoffice.settlement.dto.SettlementRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 정산 트리거/조회. /api/admin 아래라 SecurityConfig에 의해 ADMIN 전용. */
@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping("/run")
    public SettlementRunResponse run(@RequestParam String month) {
        return settlementService.runSettlement(month);
    }

    @GetMapping
    public List<SettlementResponse> list(@RequestParam String month) {
        return settlementService.getSettlements(month);
    }
}
