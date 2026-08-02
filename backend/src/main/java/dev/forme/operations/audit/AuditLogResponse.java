package dev.forme.operations.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actor,
        String action,
        String entityType,
        String entityId,
        String summary,
        Instant occurredAt
) { }
