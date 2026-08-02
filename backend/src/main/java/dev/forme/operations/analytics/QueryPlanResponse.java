package dev.forme.operations.analytics;

import java.util.List;

public record QueryPlanResponse(
        int days,
        PlanMetric rawQuery,
        PlanMetric aggregateQuery
) {
    public record PlanMetric(
            String label,
            double planningTimeMs,
            double executionTimeMs,
            List<String> planLines
    ) { }
}
