package dev.forme.operations.operationsbatch;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/operations")
public class OperationalBatchController {
    private final OperationalBatchService service;
    public OperationalBatchController(OperationalBatchService service) { this.service = service; }

    @GetMapping("/batch-jobs")
    List<BatchJobResponse> jobs() { return service.jobs(); }

    @GetMapping("/batch-executions")
    List<BatchExecutionResponse> executions(@RequestParam(required = false) BatchExecutionStatus status,
                                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return service.executions(status, limit);
    }

    @PostMapping("/batch-jobs/{jobCode}/run")
    BatchExecutionResponse run(@PathVariable String jobCode, Principal principal) {
        return service.run(jobCode, BatchTriggerType.MANUAL, principal.getName());
    }

    @PostMapping("/batch-executions/{executionId}/retry")
    BatchExecutionResponse retry(@PathVariable UUID executionId, Principal principal) {
        return service.retry(executionId, principal.getName());
    }
}
