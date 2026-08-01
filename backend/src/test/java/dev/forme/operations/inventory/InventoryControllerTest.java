package dev.forme.operations.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.forme.operations.config.SecurityConfig;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, InventoryExceptionHandler.class})
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/inventory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchesInventory() throws Exception {
        when(inventoryService.search("MLB", "ICN-01")).thenReturn(List.of(
                new InventoryPositionResponse("ICN-01", "인천 통합 물류센터", "MLB", "3ACPB014N",
                        "루키 언스트럭쳐 볼캡", "MLB-CAP-0091-BK-F", "BK", "FREE",
                        120, 12, 108, 2, Instant.parse("2026-08-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/inventory")
                        .param("query", "MLB")
                        .param("warehouseCode", "ICN-01")
                        .with(httpBasic("ops-admin", "forme-local-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("MLB-CAP-0091-BK-F"))
                .andExpect(jsonPath("$[0].availableQuantity").value(108));
    }

    @Test
    void createsMovementWithAuthenticatedActor() throws Exception {
        when(inventoryService.move(any(InventoryMovementRequest.class), eq("ops-admin")))
                .thenReturn(new InventoryMovementResponse(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "ICN-01", "MLB-CAP-0091-BK-F", InventoryMovementType.RESERVE,
                        5, 120, 17, 103, 2, false));

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .with(httpBasic("ops-admin", "forme-local-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseCode": "ICN-01",
                                  "skuCode": "MLB-CAP-0091-BK-F",
                                  "movementType": "RESERVE",
                                  "quantity": 5,
                                  "idempotencyKey": "order-1001-reserve",
                                  "reason": "출고 예약"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservedQuantity").value(17))
                .andExpect(jsonPath("$.idempotent").value(false));
    }

    @Test
    void rejectsInvalidMovement() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .with(httpBasic("ops-admin", "forme-local-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseCode": "",
                                  "skuCode": "MLB-CAP-0091-BK-F",
                                  "movementType": "RESERVE",
                                  "quantity": 0,
                                  "idempotencyKey": "",
                                  "reason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요."));
    }
}
