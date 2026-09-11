package dev.forme.operations.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WarehouseAnalyticsResponse(
        int days,
        Summary summary,
        List<DailySales> dailySales,
        List<ProductSales> topProducts,
        List<ChannelSales> channels,
        PipelineStatus pipeline,
        Instant refreshedAt) {

    public record Summary(int orderCount, int unitsSold, BigDecimal grossSales) { }
    public record DailySales(LocalDate salesDate, int orderCount, int unitsSold, BigDecimal grossSales) { }
    public record ProductSales(long productKey, String brandCode, String productName,
                               int orderCount, int unitsSold, BigDecimal grossSales) { }
    public record ChannelSales(String sourceSystem, int orderCount, int unitsSold, BigDecimal grossSales) { }
    public record PipelineStatus(String status, int extractedCount, int rejectedCount,
                                 int loadedCount, Instant completedAt) { }
}
