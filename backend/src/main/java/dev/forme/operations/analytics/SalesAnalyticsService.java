package dev.forme.operations.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesAnalyticsService {
    private static final Pattern PLANNING_TIME = Pattern.compile("Planning Time: ([0-9.]+) ms");
    private static final Pattern EXECUTION_TIME = Pattern.compile("Execution Time: ([0-9.]+) ms");
    private final JdbcTemplate jdbcTemplate;

    public SalesAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AggregateRefreshResponse refresh(int days, String actor) {
        validateDays(days);
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext('daily-sku-sales-refresh'))",
                resultSet -> null);
        jdbcTemplate.update("""
                DELETE FROM daily_sku_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                """, days);
        jdbcTemplate.update("""
                DELETE FROM daily_channel_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                """, days);
        int rows = jdbcTemplate.update("""
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
        jdbcTemplate.update("""
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
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, actor, action, entity_type, entity_id, summary)
                VALUES (gen_random_uuid(), ?, 'SALES_AGGREGATE_REFRESHED', 'SALES_AGGREGATE', ?, ?)
                """, actor, "last-" + days + "-days", "최근 %d일 판매 집계 %d행 갱신".formatted(days, rows));
        return new AggregateRefreshResponse(days, rows, Instant.now());
    }

    public SalesInventoryDashboardResponse dashboard(int days) {
        validateDays(days);
        List<SalesInventoryItemResponse> items = jdbcTemplate.query("""
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

        DashboardTotals totals = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(order_count), 0) AS order_count,
                       COALESCE(SUM(units_sold), 0) AS units_sold,
                       COALESCE(SUM(gross_sales), 0) AS gross_sales
                  FROM daily_channel_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                """, (rs, rowNum) -> new DashboardTotals(rs.getLong("order_count"),
                rs.getLong("units_sold"), rs.getBigDecimal("gross_sales")), days);
        long available = items.stream().mapToLong(SalesInventoryItemResponse::availableQuantity).sum();
        OffsetDateTime refreshed = jdbcTemplate.queryForObject(
                "SELECT MAX(refreshed_at) FROM daily_sku_sales", OffsetDateTime.class);
        return new SalesInventoryDashboardResponse(days, totals.orderCount(), totals.unitsSold(), totals.grossSales(), available,
                refreshed == null ? null : refreshed.toInstant(), items);
    }

    public QueryPlanResponse comparePlans(int days) {
        validateDays(days);
        List<String> raw = explain("""
                SELECT oi.sku_id, COUNT(DISTINCT o.id), SUM(oi.quantity), SUM(oi.quantity * oi.unit_price)
                  FROM external_orders o JOIN external_order_items oi ON oi.order_id = o.id
                 WHERE o.ordered_at >= (((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1)) AT TIME ZONE 'Asia/Seoul')
                 GROUP BY oi.sku_id
                """, days);
        List<String> aggregate = explain("""
                SELECT sku_id, SUM(order_count), SUM(units_sold), SUM(gross_sales)
                  FROM daily_sku_sales
                 WHERE sales_date >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - (? - 1))
                 GROUP BY sku_id
                """, days);
        return new QueryPlanResponse(days, metric("원본 주문 실시간 집계", raw), metric("일별 집계 테이블 조회", aggregate));
    }

    private List<String> explain(String sql, int days) {
        return jdbcTemplate.query("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql,
                (rs, rowNum) -> rs.getString(1), days);
    }

    private QueryPlanResponse.PlanMetric metric(String label, List<String> lines) {
        return new QueryPlanResponse.PlanMetric(label, findTime(lines, PLANNING_TIME),
                findTime(lines, EXECUTION_TIME), lines);
    }

    private double findTime(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) return Double.parseDouble(matcher.group(1));
        }
        return 0;
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

    private void validateDays(int days) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("조회 기간은 1일에서 365일 사이여야 합니다.");
    }

    private record DashboardTotals(long orderCount, long unitsSold, BigDecimal grossSales) { }
}
