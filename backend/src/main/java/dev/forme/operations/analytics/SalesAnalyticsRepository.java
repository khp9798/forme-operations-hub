package dev.forme.operations.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SalesAnalyticsRepository {
    private final JdbcTemplate jdbc;

    public SalesAnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void lockAggregateRefresh() {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('daily-sku-sales-refresh'))", resultSet -> null);
    }

    void deleteAggregates(int days) {
        jdbc.update("""
                DELETE FROM daily_sku_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                """, days);
        jdbc.update("""
                DELETE FROM daily_channel_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                """, days);
    }

    int upsertSkuSales(int days) {
        return jdbc.update("""
                INSERT INTO daily_sku_sales
                    (sales_date, sku_id, source_system, order_count, units_sold, gross_sales, refreshed_at)
                SELECT (o.ordered_at AT TIME ZONE 'Asia/Seoul')::date,
                       oi.sku_id, o.source_system, COUNT(DISTINCT o.id),
                       SUM(oi.quantity), SUM(oi.quantity * oi.unit_price), CURRENT_TIMESTAMP
                  FROM external_orders o
                  JOIN external_order_items oi ON oi.order_id = o.id
                 WHERE o.ordered_at >= (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1)) AT TIME ZONE 'Asia/Seoul')
                 GROUP BY (o.ordered_at AT TIME ZONE 'Asia/Seoul')::date, oi.sku_id, o.source_system
                ON CONFLICT (sales_date, sku_id, source_system) DO UPDATE
                   SET order_count = EXCLUDED.order_count,
                       units_sold = EXCLUDED.units_sold,
                       gross_sales = EXCLUDED.gross_sales,
                       refreshed_at = EXCLUDED.refreshed_at
                """, days);
    }

    void upsertChannelSales(int days) {
        jdbc.update("""
                INSERT INTO daily_channel_sales
                    (sales_date, source_system, order_count, units_sold, gross_sales, refreshed_at)
                SELECT (o.ordered_at AT TIME ZONE 'Asia/Seoul')::date, o.source_system,
                       COUNT(DISTINCT o.id), SUM(oi.quantity), SUM(oi.quantity * oi.unit_price), CURRENT_TIMESTAMP
                  FROM external_orders o
                  JOIN external_order_items oi ON oi.order_id = o.id
                 WHERE o.ordered_at >= (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1)) AT TIME ZONE 'Asia/Seoul')
                 GROUP BY (o.ordered_at AT TIME ZONE 'Asia/Seoul')::date, o.source_system
                ON CONFLICT (sales_date, source_system) DO UPDATE
                   SET order_count = EXCLUDED.order_count, units_sold = EXCLUDED.units_sold,
                       gross_sales = EXCLUDED.gross_sales, refreshed_at = EXCLUDED.refreshed_at
                """, days);
    }

    void recordAggregateRefresh(String actor, int days, int rows) {
        jdbc.update("""
                INSERT INTO audit_logs (id, actor, action, entity_type, entity_id, summary)
                VALUES (gen_random_uuid(), ?, 'SALES_AGGREGATE_REFRESHED', 'SALES_AGGREGATE', ?, ?)
                """, actor, "last-" + days + "-days", "최근 %d일 판매 집계 %d행 갱신".formatted(days, rows));
    }

    List<SalesInventoryItemResponse> findDashboardItems(int days) {
        return jdbc.query("""
                WITH sales AS (
                    SELECT sku_id, SUM(order_count) AS order_count, SUM(units_sold) AS units_sold,
                           SUM(gross_sales) AS gross_sales
                      FROM daily_sku_sales
                     WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                     GROUP BY sku_id
                ), stock AS (
                    SELECT sku_id, SUM(on_hand_quantity) AS on_hand_quantity,
                           SUM(reserved_quantity) AS reserved_quantity,
                           SUM(on_hand_quantity - reserved_quantity) AS available_quantity
                      FROM inventory_positions
                     GROUP BY sku_id
                )
                SELECT p.brand_code, p.name AS product_name, s.sku_code,
                       COALESCE(sa.order_count, 0) AS order_count,
                       COALESCE(sa.units_sold, 0) AS units_sold,
                       COALESCE(sa.gross_sales, 0) AS gross_sales,
                       COALESCE(st.on_hand_quantity, 0) AS on_hand_quantity,
                       COALESCE(st.reserved_quantity, 0) AS reserved_quantity,
                       COALESCE(st.available_quantity, 0) AS available_quantity
                  FROM skus s
                  JOIN products p ON p.id = s.product_id
                  LEFT JOIN sales sa ON sa.sku_id = s.id
                  LEFT JOIN stock st ON st.sku_id = s.id
                 WHERE s.active = TRUE AND p.active = TRUE
                 ORDER BY COALESCE(sa.gross_sales, 0) DESC, p.brand_code, s.sku_code
                """, (rs, rowNum) -> mapItem(rs), days);
    }

    DashboardTotals findDashboardTotals(int days) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(order_count), 0) AS order_count,
                       COALESCE(SUM(units_sold), 0) AS units_sold,
                       COALESCE(SUM(gross_sales), 0) AS gross_sales
                  FROM daily_channel_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                """, (rs, rowNum) -> new DashboardTotals(rs.getLong("order_count"),
                rs.getLong("units_sold"), rs.getBigDecimal("gross_sales")), days);
    }

    Instant findLastRefreshedAt() {
        OffsetDateTime refreshed = jdbc.queryForObject(
                "SELECT MAX(refreshed_at) FROM daily_sku_sales", OffsetDateTime.class);
        return refreshed == null ? null : refreshed.toInstant();
    }

    List<String> explainRawSales(int days) {
        return explain("""
                SELECT oi.sku_id, COUNT(DISTINCT o.id), SUM(oi.quantity), SUM(oi.quantity * oi.unit_price)
                  FROM external_orders o JOIN external_order_items oi ON oi.order_id = o.id
                 WHERE o.ordered_at >= (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1)) AT TIME ZONE 'Asia/Seoul')
                 GROUP BY oi.sku_id
                """, days);
    }

    List<String> explainAggregatedSales(int days) {
        return explain("""
                SELECT sku_id, SUM(order_count), SUM(units_sold), SUM(gross_sales)
                  FROM daily_sku_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                 GROUP BY sku_id
                """, days);
    }

    void lockBenchmarkGeneration() {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext('sales-query-benchmark-seed'))", resultSet -> null);
    }

    void replaceBenchmarkData(int rows) {
        jdbc.execute("TRUNCATE sales_query_benchmark_heap, sales_query_benchmark_indexed");
        jdbc.update("""
                INSERT INTO sales_query_benchmark_heap (id, ordered_at, sku_code, quantity, gross_amount)
                SELECT sequence,
                       CURRENT_TIMESTAMP - ((sequence % 365) * INTERVAL '1 day') - ((sequence % 86400) * INTERVAL '1 second'),
                       'BENCH-SKU-' || LPAD((sequence % 1000)::text, 4, '0'),
                       ((sequence % 3) + 1)::integer,
                       (((sequence % 3) + 1) * (10000 + (sequence % 90000)))::numeric(18, 2)
                  FROM generate_series(1, ?) AS sequence
                """, rows);
        jdbc.execute("INSERT INTO sales_query_benchmark_indexed SELECT * FROM sales_query_benchmark_heap");
        jdbc.execute("ANALYZE sales_query_benchmark_heap");
        jdbc.execute("ANALYZE sales_query_benchmark_indexed");
    }

    void recordBenchmarkGeneration(String actor, int rows) {
        jdbc.update("""
                INSERT INTO audit_logs (id, actor, action, entity_type, entity_id, summary)
                VALUES (gen_random_uuid(), ?, 'QUERY_BENCHMARK_DATA_GENERATED', 'QUERY_BENCHMARK', ?, ?)
                """, actor, "sales-index-comparison", "SQL 성능 비교 샘플 %,d행 생성".formatted(rows));
    }

    int countBenchmarkRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sales_query_benchmark_heap", Integer.class);
    }

    List<String> explainBenchmarkWithoutIndex(int days) {
        return explainBenchmark("sales_query_benchmark_heap", days);
    }

    List<String> explainBenchmarkWithIndex(int days) {
        return explainBenchmark("sales_query_benchmark_indexed", days);
    }

    private List<String> explainBenchmark(String table, int days) {
        return explain("""
                SELECT sku_code, SUM(quantity), SUM(gross_amount)
                  FROM %s
                 WHERE ordered_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                 GROUP BY sku_code
                 ORDER BY SUM(gross_amount) DESC
                 LIMIT 50
                """.formatted(table), days);
    }

    private List<String> explain(String sql, int days) {
        return jdbc.query("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql,
                (rs, rowNum) -> rs.getString(1), days);
    }

    private SalesInventoryItemResponse mapItem(ResultSet rs) throws SQLException {
        long unitsSold = rs.getLong("units_sold");
        long onHand = rs.getLong("on_hand_quantity");
        BigDecimal sellThrough = unitsSold + onHand == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(unitsSold)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(unitsSold + onHand), 1, RoundingMode.HALF_UP);
        return new SalesInventoryItemResponse(rs.getString("brand_code"), rs.getString("product_name"),
                rs.getString("sku_code"), rs.getLong("order_count"), unitsSold,
                rs.getBigDecimal("gross_sales"), onHand, rs.getLong("reserved_quantity"),
                rs.getLong("available_quantity"), sellThrough);
    }

    record DashboardTotals(long orderCount, long unitsSold, BigDecimal grossSales) { }
}
