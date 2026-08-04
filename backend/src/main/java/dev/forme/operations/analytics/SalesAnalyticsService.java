package dev.forme.operations.analytics;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesAnalyticsService {
    private static final Pattern PLANNING_TIME = Pattern.compile("Planning Time: ([0-9.]+) ms");
    private static final Pattern EXECUTION_TIME = Pattern.compile("Execution Time: ([0-9.]+) ms");
    private final SalesAnalyticsRepository repository;

    public SalesAnalyticsService(SalesAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AggregateRefreshResponse refresh(int days, String actor) {
        validateDays(days);
        repository.lockAggregateRefresh();
        repository.deleteAggregates(days);
        int rows = repository.upsertSkuSales(days);
        repository.upsertChannelSales(days);
        repository.recordAggregateRefresh(actor, days, rows);
        return new AggregateRefreshResponse(days, rows, Instant.now());
    }

    public SalesInventoryDashboardResponse dashboard(int days) {
        validateDays(days);
        List<SalesInventoryItemResponse> items = repository.findDashboardItems(days);
        SalesAnalyticsRepository.DashboardTotals totals = repository.findDashboardTotals(days);
        long available = items.stream().mapToLong(SalesInventoryItemResponse::availableQuantity).sum();
        return new SalesInventoryDashboardResponse(days, totals.orderCount(), totals.unitsSold(), totals.grossSales(),
                available, repository.findLastRefreshedAt(), items);
    }

    public QueryPlanResponse comparePlans(int days) {
        validateDays(days);
        return new QueryPlanResponse(days,
                metric("원본 주문 실시간 집계", repository.explainRawSales(days)),
                metric("일별 집계 테이블 조회", repository.explainAggregatedSales(days)));
    }

    @Transactional
    public BenchmarkSeedResponse generateBenchmarkData(int rows, String actor) {
        if (rows < 10_000 || rows > 500_000) {
            throw new IllegalArgumentException("샘플 데이터는 10,000행에서 500,000행 사이여야 합니다.");
        }
        repository.lockBenchmarkGeneration();
        repository.replaceBenchmarkData(rows);
        repository.recordBenchmarkGeneration(actor, rows);
        return new BenchmarkSeedResponse(rows, Math.min(rows, 1000), Instant.now());
    }

    public IndexBenchmarkResponse compareIndexPlans(int days) {
        validateDays(days);
        int rows = repository.countBenchmarkRows();
        if (rows == 0) {
            throw new IllegalStateException("먼저 성능 비교용 샘플 데이터를 생성해 주세요.");
        }
        return new IndexBenchmarkResponse(days, rows,
                metric("인덱스 없는 원본 테이블", repository.explainBenchmarkWithoutIndex(days)),
                metric("복합 커버링 인덱스 적용", repository.explainBenchmarkWithIndex(days)));
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

    private void validateDays(int days) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("조회 기간은 1일에서 365일 사이여야 합니다.");
    }
}
