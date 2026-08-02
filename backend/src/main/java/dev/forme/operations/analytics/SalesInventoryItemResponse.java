package dev.forme.operations.analytics;

import java.math.BigDecimal;

public record SalesInventoryItemResponse(
        String brandCode,
        String productName,
        String skuCode,
        long orderCount,
        long unitsSold,
        BigDecimal grossSales,
        long onHandQuantity,
        long reservedQuantity,
        long availableQuantity,
        BigDecimal sellThroughRate
) { }
