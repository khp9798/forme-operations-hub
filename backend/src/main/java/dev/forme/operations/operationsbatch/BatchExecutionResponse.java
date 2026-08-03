package dev.forme.operations.operationsbatch;

import java.time.Instant;
import java.util.UUID;

public record BatchExecutionResponse(
        UUID id,
        String jobCode,
        String jobName,
        BatchTriggerType triggerType,
        BatchExecutionStatus status,
        int attemptNumber,
        UUID retryOf,
        String requestedBy,
        Instant startedAt,
        Instant completedAt,
        int processedCount,
        String resultSummary,
        String errorMessage
) { }
