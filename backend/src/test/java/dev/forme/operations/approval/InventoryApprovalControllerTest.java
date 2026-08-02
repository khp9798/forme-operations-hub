package dev.forme.operations.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.forme.operations.config.SecurityConfig;

@WebMvcTest(InventoryApprovalController.class)
@Import(SecurityConfig.class)
class InventoryApprovalControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean InventoryApprovalService service;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void operatorCanCreateRequestButCannotReadApprovalQueue() throws Exception {
        when(service.create(any(), eq("operator"))).thenReturn(null);
        mockMvc.perform(post("/api/v1/inventory/adjustment-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseCode":"ICN-01","skuCode":"SKU-1","movementType":"ADJUSTMENT_IN","quantity":1,"reason":"검수"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/approvals/inventory-adjustments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "approver", roles = "APPROVER")
    void approverCanReadAndDecideButCannotCreateRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.search(null)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/approvals/inventory-adjustments"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/approvals/inventory-adjustments/{id}/decision", requestId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"확인\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/adjustment-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseCode":"ICN-01","skuCode":"SKU-1","movementType":"ADJUSTMENT_IN","quantity":1,"reason":"검수"}
                                """))
                .andExpect(status().isForbidden());
    }
}
