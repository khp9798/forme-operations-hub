package dev.forme.operations.approval;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryApprovalController {

    private final InventoryApprovalService approvalService;

    public InventoryApprovalController(InventoryApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/inventory/adjustment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    AdjustmentRequestResponse create(@Valid @RequestBody AdjustmentRequestCreate request, Principal principal) {
        return approvalService.create(request, principal.getName());
    }

    @GetMapping("/approvals/inventory-adjustments")
    List<AdjustmentRequestResponse> search(@RequestParam(required = false) AdjustmentStatus status) {
        return approvalService.search(status);
    }

    @PostMapping("/approvals/inventory-adjustments/{requestId}/decision")
    AdjustmentRequestResponse decide(@PathVariable UUID requestId,
                                     @Valid @RequestBody ApprovalDecisionRequest request,
                                     Principal principal) {
        return approvalService.decide(requestId, request, principal.getName());
    }
}
