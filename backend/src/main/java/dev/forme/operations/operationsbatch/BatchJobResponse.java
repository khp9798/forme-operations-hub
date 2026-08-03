package dev.forme.operations.operationsbatch;

import java.time.Instant;

public record BatchJobResponse(
        String jobCode,
        String name,
        String description,
        String cronExpression,
        boolean enabled,
        int maxRetryCount,
        BatchExecutionStatus lastStatus,
        Instant lastStartedAt,
        Instant lastCompletedAt
) { }
