package dev.forme.operations.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SalesInventoryDashboardResponse(
        int days,
        long orderCount,
        long unitsSold,
        BigDecimal grossSales,
        long availableQuantity,
        Instant lastRefreshedAt,
        List<SalesInventoryItemResponse> items
) { }
