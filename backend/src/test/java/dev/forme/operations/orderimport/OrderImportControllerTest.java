package dev.forme.operations.orderimport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.forme.operations.config.SecurityConfig;

@WebMvcTest(OrderImportController.class)
@Import({SecurityConfig.class, OrderImportExceptionHandler.class})
class OrderImportControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean OrderImportService orderImportService;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/order-imports/validate")
                        .file(new MockMultipartFile("file", "orders.csv", "text/csv", "header".getBytes()))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ops-admin", roles = "OPERATOR")
    void validatesUploadedCsvAsAuthenticatedOperator() throws Exception {
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(orderImportService.validate(any(), eq("ops-admin"))).thenReturn(
                new OrderImportResponse(jobId, "orders.csv", "COMPLETED", 1, 1, 0,
                        List.of(new OrderImportRowResponse(2, "ORDER-1", "MLB-CAP-0091-BK-F",
                                1, java.math.BigDecimal.valueOf(39000), "VALID", List.of()))));

        mockMvc.perform(multipart("/api/v1/order-imports/validate")
                        .file(new MockMultipartFile("file", "orders.csv", "text/csv", "header".getBytes()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.validCount").value(1));
    }
}
