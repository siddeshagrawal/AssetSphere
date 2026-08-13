package com.assetsphere.modules.billing.api;

import com.assetsphere.modules.billing.api.dto.response.PlanResponse;
import com.assetsphere.modules.billing.application.BillingService;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/billing/plans")
@SecurityRequirement(name = "bearerAuth")
class BillingPlanController {
    private final BillingService billing;
    private final ClockProvider clock;

    @GetMapping
    ApiResponse<List<PlanResponse>> plans() {
        return ApiResponse.success(billing.plans(), clock);
    }
}
