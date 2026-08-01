package dev.forme.operations.inventory;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    List<InventoryPositionResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String warehouseCode) {
        return inventoryService.search(query, warehouseCode);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    InventoryMovementResponse move(@Valid @RequestBody InventoryMovementRequest request, Principal principal) {
        return inventoryService.move(request, principal.getName());
    }
}
