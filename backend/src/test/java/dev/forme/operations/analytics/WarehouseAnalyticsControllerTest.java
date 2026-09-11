package dev.forme.operations.analytics;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import dev.forme.operations.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WarehouseAnalyticsController.class)
@Import({SecurityConfig.class, AnalyticsExceptionHandler.class})
class WarehouseAnalyticsControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean WarehouseAnalyticsClient client;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanReadWarehouseAnalytics() throws Exception {
        when(client.overview(30)).thenReturn(new WarehouseAnalyticsResponse(
                30, new WarehouseAnalyticsResponse.Summary(2, 3, new BigDecimal("354000")),
                List.of(), List.of(), List.of(), null, null));

        mockMvc.perform(get("/api/v1/analytics/warehouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orderCount").value(2));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void unavailableAnalyticsReturnsServiceUnavailable() throws Exception {
        when(client.overview(30)).thenThrow(new AnalyticsServiceUnavailableException("판매 분석 서비스에 연결할 수 없습니다."));

        mockMvc.perform(get("/api/v1/analytics/warehouse"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("판매 분석 서비스에 연결할 수 없습니다."));
    }
}
