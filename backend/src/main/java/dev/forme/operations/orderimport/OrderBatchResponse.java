package dev.forme.operations.orderimport;

import java.util.UUID;

public record OrderBatchResponse(
        UUID importJobId,
        long batchExecutionId,
        String status,
        long readCount,
        long writeCount,
        int processedCount,
        int remainingCount,
        int invalidCount) {
}
