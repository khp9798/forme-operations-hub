package dev.forme.operations.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/analytics/warehouse")
public class WarehouseAnalyticsController {
    private final WarehouseAnalyticsClient analyticsClient;

    public WarehouseAnalyticsController(WarehouseAnalyticsClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    @GetMapping
    WarehouseAnalyticsResponse overview(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return analyticsClient.overview(days);
    }
}

