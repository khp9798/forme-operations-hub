package dev.forme.operations.operationsbatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import dev.forme.operations.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AirflowOperationsController.class)
@Import(SecurityConfig.class)
class AirflowOperationsControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AirflowOperationsService service;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void operatorCanReadRejectedItemsButCannotTriggerBackfill() throws Exception {
        when(service.rejected(null, 50)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/operations/airflow/rejected-items"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/operations/airflow/backfills").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanTriggerBackfill() throws Exception {
        when(service.trigger(any(), eq("admin"))).thenReturn(new AirflowDagRunResponse(
                "manual__test", "queued", Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-03T00:00:00Z"), true));
        mockMvc.perform(post("/api/v1/operations/airflow/backfills").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rejectsInvalidInterval() throws Exception {
        mockMvc.perform(post("/api/v1/operations/airflow/backfills").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intervalStart":"2026-08-03T00:00:00Z","intervalEnd":"2026-08-02T00:00:00Z","reprocessRejected":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String requestJson() {
        return """
                {"intervalStart":"2026-08-02T00:00:00Z","intervalEnd":"2026-08-03T00:00:00Z","reprocessRejected":true}
                """;
    }
}
