package dev.forme.operations.operationsbatch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class AirflowClient {
    private static final Logger log = LoggerFactory.getLogger(AirflowClient.class);
    private static final String DAG_ID = "forme_daily_sales_pipeline";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String username;
    private final String password;

    public AirflowClient(
            @Value("${forme.airflow.base-url:http://localhost:8081}") String baseUrl,
            @Value("${forme.airflow.username:admin}") String username,
            @Value("${forme.airflow.password:admin}") String password,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper;
    }

    AirflowDagRunResponse trigger(AirflowBackfillRequest request) {
        try {
            Map<String, Object> tokenResponse = post("/auth/token",
                    Map.of("username", username, "password", password), null);
            String token = tokenResponse == null ? null : (String) tokenResponse.get("access_token");
            if (token == null || token.isBlank()) {
                throw new OperationalBatchConflictException("Airflow 인증 토큰을 발급받지 못했습니다.");
            }

            String dagRunId = "manual__ops_backfill__" + UUID.randomUUID();
            Instant now = Instant.now();
            Map<String, Object> body = Map.of(
                    "dag_run_id", dagRunId,
                    "logical_date", now.toString(),
                    "data_interval_start", request.intervalStart().toString(),
                    "data_interval_end", request.intervalEnd().toString(),
                    "conf", Map.of("reprocess_rejected", request.reprocessRejected()),
                    "note", "Triggered from FORME Operations Hub"
            );
            Map<String, Object> response = post("/api/v2/dags/" + DAG_ID + "/dagRuns", body, token);
            String state = response == null ? "queued" : String.valueOf(response.getOrDefault("state", "queued"));
            return new AirflowDagRunResponse(dagRunId, state, request.intervalStart(), request.intervalEnd(),
                    request.reprocessRejected());
        } catch (IOException exception) {
            log.error("Airflow backfill request failed", exception);
            throw new OperationalBatchConflictException("Airflow에 백필 작업을 요청하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OperationalBatchConflictException("Airflow 요청이 중단되었습니다.");
        }
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String bearerToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (bearerToken != null) request.header("Authorization", "Bearer " + bearerToken);
        HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Airflow HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() { });
    }
}
