package dev.forme.operations.operationsbatch;

import java.util.List;
import java.util.UUID;

import dev.forme.operations.analytics.AggregateRefreshResponse;
import dev.forme.operations.analytics.SalesAnalyticsService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OperationalBatchService {
    private final OperationalBatchRepository repository;
    private final TransactionTemplate transactions;
    private final SalesAnalyticsService analytics;

    public OperationalBatchService(OperationalBatchRepository repository, TransactionTemplate transactions, SalesAnalyticsService analytics) {
        this.repository = repository;
        this.transactions = transactions;
        this.analytics = analytics;
    }

    public List<BatchJobResponse> jobs() {
        return repository.findJobs();
    }

    public List<BatchExecutionResponse> executions(BatchExecutionStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findExecutions(status, safeLimit);
    }

    public BatchExecutionResponse run(String jobCode, BatchTriggerType trigger, String actor) {
        OperationalBatchRepository.JobDefinition job = repository.findJob(jobCode);
        if (!job.enabled()) throw new OperationalBatchConflictException("비활성화된 배치 작업입니다.");
        return execute(job, trigger, actor, 1, null);
    }

    public BatchExecutionResponse retry(UUID executionId, String actor) {
        OperationalBatchRepository.PreviousExecution previous = repository.findPrevious(executionId);
        if (previous.status() != BatchExecutionStatus.FAILED)
            throw new OperationalBatchConflictException("실패한 실행만 재시도할 수 있습니다.");
        OperationalBatchRepository.JobDefinition job = repository.findJob(previous.jobCode());
        int attempt = previous.attemptNumber() + 1;
        if (attempt > job.maxRetryCount() + 1)
            throw new OperationalBatchConflictException("최대 재시도 횟수를 초과했습니다.");
        UUID root = previous.retryOf() == null ? previous.id() : previous.retryOf();
        return execute(job, BatchTriggerType.RETRY, actor, attempt, root);
    }

    private BatchExecutionResponse execute(OperationalBatchRepository.JobDefinition job, BatchTriggerType trigger, String actor,
                                             int attempt, UUID retryOf) {
        UUID id = UUID.randomUUID();
        try {
            transactions.executeWithoutResult(tx -> repository.insertRunning(id, job, trigger, attempt, retryOf, actor));
        } catch (DataIntegrityViolationException error) {
            throw new OperationalBatchConflictException("이미 실행 중인 배치 작업입니다.");
        }
        try {
            BatchResult result = invoke(job.handlerCode(), actor);
            transactions.executeWithoutResult(tx -> repository.complete(id, result.processedCount(), result.summary()));
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (message.length() > 1000) message = message.substring(0, 1000);
            String finalMessage = message;
            transactions.executeWithoutResult(tx -> repository.fail(id, finalMessage));
        }
        return repository.findExecution(id);
    }

    private BatchResult invoke(String handler, String actor) {
        if ("SALES_AGGREGATE_90D".equals(handler)) {
            AggregateRefreshResponse result = analytics.refresh(90, actor);
            return new BatchResult(result.aggregateRows(), "최근 90일 판매 집계 %d행 갱신".formatted(result.aggregateRows()));
        }
        throw new IllegalStateException("등록되지 않은 배치 핸들러입니다: " + handler);
    }

    private record BatchResult(long processedCount, String summary) { }
}
