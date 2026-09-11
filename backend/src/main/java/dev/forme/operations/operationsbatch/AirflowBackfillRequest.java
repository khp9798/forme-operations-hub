package dev.forme.operations.operationsbatch;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AirflowBackfillRequest(
        @NotNull Instant intervalStart,
        @NotNull Instant intervalEnd,
        boolean reprocessRejected) {

    @AssertTrue(message = "종료 시각은 시작 시각보다 늦고 최대 31일 이내여야 합니다.")
    public boolean isValidInterval() {
        return intervalStart != null && intervalEnd != null
                && intervalEnd.isAfter(intervalStart)
                && intervalEnd.isBefore(intervalStart.plusSeconds(31L * 24 * 60 * 60 + 1));
    }
}

