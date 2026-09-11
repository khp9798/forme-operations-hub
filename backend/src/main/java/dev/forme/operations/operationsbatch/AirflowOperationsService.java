package dev.forme.operations.operationsbatch;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AirflowOperationsService {
    private final AirflowClient airflowClient;
    private final AirflowOperationsRepository repository;

    public AirflowOperationsService(AirflowClient airflowClient, AirflowOperationsRepository repository) {
        this.airflowClient = airflowClient;
        this.repository = repository;
    }

    List<RejectedOrderItemResponse> rejected(String status, int limit) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.isEmpty() && !normalized.equals("PENDING") && !normalized.equals("RESOLVED")) {
            throw new OperationalBatchConflictException("오류 처리 상태가 올바르지 않습니다.");
        }
        return repository.findRejected(normalized, limit);
    }

    AirflowDagRunResponse trigger(AirflowBackfillRequest request, String actor) {
        if (request.intervalEnd().isAfter(Instant.now().plusSeconds(60))) {
            throw new OperationalBatchConflictException("미래 기간은 백필할 수 없습니다.");
        }
        AirflowDagRunResponse response = airflowClient.trigger(request);
        repository.writeTriggerAudit(actor, response);
        return response;
    }
}
