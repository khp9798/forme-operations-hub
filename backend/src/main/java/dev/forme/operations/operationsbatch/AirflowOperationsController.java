package dev.forme.operations.operationsbatch;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/operations/airflow")
public class AirflowOperationsController {
    private final AirflowOperationsService service;

    public AirflowOperationsController(AirflowOperationsService service) {
        this.service = service;
    }

    @GetMapping("/rejected-items")
    List<RejectedOrderItemResponse> rejected(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return service.rejected(status, limit);
    }

    @PostMapping("/backfills")
    AirflowDagRunResponse trigger(@Valid @RequestBody AirflowBackfillRequest request, Principal principal) {
        return service.trigger(request, principal.getName());
    }
}

