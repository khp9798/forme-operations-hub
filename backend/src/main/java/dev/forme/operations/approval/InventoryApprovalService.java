package dev.forme.operations.approval;

import java.util.List;
import java.util.UUID;

import dev.forme.operations.inventory.InventoryMovementRequest;
import dev.forme.operations.inventory.InventoryMovementResponse;
import dev.forme.operations.inventory.InventoryMovementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryApprovalService {

    private final InventoryApprovalRepository repository;
    private final dev.forme.operations.inventory.InventoryService inventoryService;

    public InventoryApprovalService(InventoryApprovalRepository repository,
                                    dev.forme.operations.inventory.InventoryService inventoryService) {
        this.repository = repository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public AdjustmentRequestResponse create(AdjustmentRequestCreate request, String actor) {
        InventoryApprovalRepository.Target target = repository.findTarget(request.warehouseCode(), request.skuCode());
        UUID requestId = UUID.randomUUID();
        repository.insertRequest(requestId, target, request, actor);
        repository.writeAudit(actor, "INVENTORY_ADJUSTMENT_REQUESTED", requestId,
                "%s · %s · %s %d개 조정 요청".formatted(
                        request.warehouseCode(), request.skuCode(), request.movementType(), request.quantity()));
        return repository.findById(requestId);
    }

    public List<AdjustmentRequestResponse> search(AdjustmentStatus status) {
        return repository.search(status);
    }

    @Transactional
    public AdjustmentRequestResponse decide(UUID requestId, ApprovalDecisionRequest decision, String actor) {
        InventoryApprovalRepository.LockedRequest request = repository.lockRequest(requestId);
        if (request.status() != AdjustmentStatus.PENDING) {
            throw new ApprovalConflictException("이미 처리된 승인 요청입니다.");
        }
        if (request.requestedBy().equals(actor)) {
            throw new ApprovalConflictException("요청자와 승인자는 같을 수 없습니다.");
        }

        String comment = decision.comment() == null ? "" : decision.comment().trim();
        UUID movementId = null;
        AdjustmentStatus nextStatus;
        String action;
        if (decision.decision() == ApprovalDecision.APPROVE) {
            InventoryMovementResponse movement = inventoryService.move(new InventoryMovementRequest(
                    request.warehouseCode(), request.skuCode(), request.movementType(), request.quantity(),
                    "approval-" + requestId, request.reason()), actor);
            movementId = movement.movementId();
            nextStatus = AdjustmentStatus.APPROVED;
            action = "INVENTORY_ADJUSTMENT_APPROVED";
        } else {
            if (comment.isBlank()) {
                throw new ApprovalConflictException("거절 사유를 입력해 주세요.");
            }
            nextStatus = AdjustmentStatus.REJECTED;
            action = "INVENTORY_ADJUSTMENT_REJECTED";
        }

        repository.decide(requestId, nextStatus, actor, comment, movementId);
        repository.writeAudit(actor, action, requestId,
                "%s · %s · 요청자 %s · %s".formatted(
                        request.warehouseCode(), request.skuCode(), request.requestedBy(),
                        comment.isBlank() ? "의견 없음" : comment));
        return repository.findById(requestId);
    }
}
