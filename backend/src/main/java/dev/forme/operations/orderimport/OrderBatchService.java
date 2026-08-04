package dev.forme.operations.orderimport;

import java.util.UUID;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderBatchService {
    private final JobOperator jobOperator;
    private final Job orderImportJob;
    private final OrderBatchRepository repository;

    public OrderBatchService(JobOperator jobOperator,
                             @Qualifier("orderImportJob") Job orderImportJob,
                             OrderBatchRepository repository) {
        this.jobOperator = jobOperator;
        this.orderImportJob = orderImportJob;
        this.repository = repository;
    }

    public OrderBatchResponse process(UUID importJobId, String actor) {
        if (!repository.validationJobExists(importJobId))
            throw new OrderImportValidationException("처리할 주문 업로드 작업을 찾지 못했습니다.");

        repository.markRunning(importJobId, actor);
        try {
            JobExecution execution = jobOperator.start(orderImportJob, new JobParametersBuilder()
                    .addString("importJobId", importJobId.toString())
                    .addString("attemptId", UUID.randomUUID().toString())
                    .toJobParameters());
            StepExecution step = execution.getStepExecutions().stream().findFirst().orElse(null);
            int processed = count(importJobId, "PROCESSED");
            int remaining = countPendingValid(importJobId);
            int invalid = countInvalid(importJobId);
            String integrationStatus = execution.getStatus().isUnsuccessful() ? "FAILED"
                    : invalid > 0 || remaining > 0 ? "PARTIAL_FAILED" : "COMPLETED";
            repository.complete(importJobId, integrationStatus, processed, invalid + remaining);
            return new OrderBatchResponse(importJobId, execution.getId(), execution.getStatus().name(),
                    step == null ? 0 : step.getReadCount(), step == null ? 0 : step.getWriteCount(),
                    processed, remaining, invalid);
        } catch (Exception exception) {
            repository.fail(importJobId);
            throw new IllegalStateException("주문 배치를 실행하지 못했습니다.", exception);
        }
    }

    private int count(UUID jobId, String status) {
        return repository.countByProcessingStatus(jobId, status);
    }

    private int countPendingValid(UUID jobId) {
        return repository.countPendingValid(jobId);
    }

    private int countInvalid(UUID jobId) {
        return repository.countInvalid(jobId);
    }
}
