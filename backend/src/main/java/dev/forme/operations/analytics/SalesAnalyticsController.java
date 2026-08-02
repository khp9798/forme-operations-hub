package dev.forme.operations.analytics;

import java.security.Principal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/analytics/sales-inventory")
public class SalesAnalyticsController {
    private final SalesAnalyticsService salesAnalyticsService;

    public SalesAnalyticsController(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
    }

    @GetMapping
    SalesInventoryDashboardResponse dashboard(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return salesAnalyticsService.dashboard(days);
    }

    @PostMapping("/refresh")
    AggregateRefreshResponse refresh(
            @RequestParam(defaultValue = "90") @Min(1) @Max(365) int days,
            Principal principal) {
        return salesAnalyticsService.refresh(days, principal.getName());
    }

    @GetMapping("/query-plans")
    QueryPlanResponse queryPlans(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return salesAnalyticsService.comparePlans(days);
    }
}
