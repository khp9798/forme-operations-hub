package dev.forme.operations.analytics;

public record IndexBenchmarkResponse(
        int days,
        int sampleRows,
        QueryPlanResponse.PlanMetric withoutIndex,
        QueryPlanResponse.PlanMetric withIndex
) { }

