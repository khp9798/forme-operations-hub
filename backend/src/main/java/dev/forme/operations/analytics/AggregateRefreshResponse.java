package dev.forme.operations.analytics;

import java.time.Instant;

public record AggregateRefreshResponse(
        int days,
        int aggregateRows,
        Instant refreshedAt
) { }
