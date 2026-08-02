package dev.forme.operations.analytics;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dev.forme.operations.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SalesAnalyticsController.class)
@Import(SecurityConfig.class)
class SalesAnalyticsControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SalesAnalyticsService service;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void operatorCanReadAnalyticsButCannotRefreshAggregates() throws Exception {
        when(service.dashboard(30)).thenReturn(new SalesInventoryDashboardResponse(
                30, 0, 0, BigDecimal.ZERO, 0, null, List.of()));
        mockMvc.perform(get("/api/v1/analytics/sales-inventory"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/analytics/sales-inventory/refresh").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanRefreshAggregates() throws Exception {
        when(service.refresh(eq(90), eq("admin"))).thenReturn(new AggregateRefreshResponse(90, 2, Instant.now()));
        mockMvc.perform(post("/api/v1/analytics/sales-inventory/refresh").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousUserCannotReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/sales-inventory"))
                .andExpect(status().isUnauthorized());
    }
}
