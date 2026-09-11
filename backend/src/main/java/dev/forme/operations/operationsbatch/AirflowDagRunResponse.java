package dev.forme.operations.operationsbatch;

import java.time.Instant;

public record AirflowDagRunResponse(
        String dagRunId,
        String state,
        Instant intervalStart,
        Instant intervalEnd,
        boolean reprocessRejected) { }

