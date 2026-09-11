package dev.forme.operations.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WarehouseAnalyticsClient {
    private final RestClient restClient;

    public WarehouseAnalyticsClient(
            @Value("${forme.analytics.base-url:http://localhost:8001}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public WarehouseAnalyticsResponse overview(int days) {
        try {
            WarehouseAnalyticsResponse response = restClient.get()
                    .uri(uri -> uri.path("/api/v1/sales/overview").queryParam("days", days).build())
                    .retrieve()
                    .body(WarehouseAnalyticsResponse.class);
            if (response == null) {
                throw new AnalyticsServiceUnavailableException("분석 서비스에서 빈 응답을 반환했습니다.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new AnalyticsServiceUnavailableException("판매 분석 서비스에 연결할 수 없습니다.", exception);
        }
    }
}
