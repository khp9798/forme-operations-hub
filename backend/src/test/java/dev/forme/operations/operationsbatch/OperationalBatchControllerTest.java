package dev.forme.operations.operationsbatch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.forme.operations.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationalBatchController.class)
@Import(SecurityConfig.class)
class OperationalBatchControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalBatchService service;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void operatorCanReadButCannotRunJobs() throws Exception {
        when(service.jobs()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/operations/batch-jobs")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/operations/batch-jobs/DAILY_SALES_AGGREGATE/run").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanRunAndRetryFailedJobs() throws Exception {
        UUID id = UUID.randomUUID();
        BatchExecutionResponse response = new BatchExecutionResponse(id, "JOB", "작업", BatchTriggerType.MANUAL,
                BatchExecutionStatus.COMPLETED, 1, null, "admin", Instant.now(), Instant.now(), 1, "완료", null);
        when(service.run(eq("JOB"), eq(BatchTriggerType.MANUAL), eq("admin"))).thenReturn(response);
        when(service.retry(eq(id), eq("admin"))).thenReturn(response);
        mockMvc.perform(post("/api/v1/operations/batch-jobs/JOB/run").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/operations/batch-executions/{id}/retry", id).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void anonymousCannotReadExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/operations/batch-executions")).andExpect(status().isUnauthorized());
    }
}
