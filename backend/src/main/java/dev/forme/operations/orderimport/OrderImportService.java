package dev.forme.operations.orderimport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderImportService {
    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    static final int MAX_ROWS = 5_000;
    static final List<String> HEADERS = List.of(
            "source_order_id", "ordered_at", "sku_code", "quantity", "unit_price", "currency",
            "recipient_name", "postal_code", "address_line1", "address_line2");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrderImportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderImportResponse validate(MultipartFile file, String actor) {
        validateFile(file);
        List<ParsedRow> parsedRows = parse(file);
        if (parsedRows.isEmpty()) throw new OrderImportValidationException("주문 데이터가 한 행 이상 필요합니다.");

        Set<String> knownSkus = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT sku_code FROM skus WHERE active = TRUE", String.class));
        Set<String> orderSkuPairs = new HashSet<>();
        List<ValidatedRow> rows = parsedRows.stream()
                .map(row -> validateRow(row, knownSkus, orderSkuPairs))
                .toList();

        int validCount = (int) rows.stream().filter(ValidatedRow::valid).count();
        int invalidCount = rows.size() - validCount;
        String status = invalidCount == 0 ? "COMPLETED" : "PARTIAL_FAILED";
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO integration_jobs
                    (id, source_system, job_type, source_file_name, status, total_count,
                     success_count, failure_count, requested_by, started_at, completed_at)
                VALUES (?, 'CSV_UPLOAD', 'ORDER_VALIDATION', ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, jobId, safeFileName(file.getOriginalFilename()), status, rows.size(), validCount, invalidCount, actor);

        for (ValidatedRow row : rows) insertRow(jobId, row);
        return new OrderImportResponse(jobId, safeFileName(file.getOriginalFilename()), status,
                rows.size(), validCount, invalidCount, rows.stream().map(ValidatedRow::response).toList());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new OrderImportValidationException("CSV 파일을 선택해 주세요.");
        if (file.getSize() > MAX_FILE_SIZE) throw new OrderImportValidationException("CSV 파일은 5MB 이하여야 합니다.");
        String name = safeFileName(file.getOriginalFilename()).toLowerCase();
        if (!name.endsWith(".csv")) throw new OrderImportValidationException(".csv 형식의 파일만 업로드할 수 있습니다.");
    }

    private List<ParsedRow> parse(MultipartFile file) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            reader.mark(1);
            if (reader.read() != '\uFEFF') reader.reset();
            try (CSVParser parser = format.parse(reader)) {
                List<String> actualHeaders = new ArrayList<>(parser.getHeaderMap().keySet());
                if (!actualHeaders.equals(HEADERS)) {
                    throw new OrderImportValidationException("CSV 헤더가 템플릿과 다릅니다. 제공된 양식을 사용해 주세요.");
                }
                List<ParsedRow> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    if (rows.size() >= MAX_ROWS) throw new OrderImportValidationException("한 번에 최대 5,000행까지 검증할 수 있습니다.");
                    Map<String, String> values = new LinkedHashMap<>();
                    HEADERS.forEach(header -> values.put(header, record.get(header)));
                    rows.add(new ParsedRow(Math.toIntExact(record.getRecordNumber() + 1), values));
                }
                return rows;
            }
        } catch (OrderImportValidationException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new OrderImportValidationException("CSV 파일을 읽지 못했습니다. UTF-8 인코딩과 열 구성을 확인해 주세요.");
        }
    }

    private ValidatedRow validateRow(ParsedRow row, Set<String> knownSkus, Set<String> orderSkuPairs) {
        Map<String, String> value = row.values();
        List<String> errors = new ArrayList<>();
        String orderId = value.get("source_order_id");
        String skuCode = value.get("sku_code");
        if (orderId.isBlank()) errors.add("주문번호가 비어 있습니다.");
        if (skuCode.isBlank()) errors.add("SKU가 비어 있습니다.");
        else if (!knownSkus.contains(skuCode)) errors.add("등록되지 않은 SKU입니다.");
        if (!orderId.isBlank() && !skuCode.isBlank() && !orderSkuPairs.add(orderId + "\u0000" + skuCode)) {
            errors.add("파일 안에 같은 주문번호와 SKU가 중복되었습니다.");
        }

        OffsetDateTime orderedAt = parseDate(value.get("ordered_at"), errors);
        Integer quantity = parsePositiveInteger(value.get("quantity"), "수량", errors);
        BigDecimal unitPrice = parsePositiveDecimal(value.get("unit_price"), "단가", errors);
        String currency = value.get("currency").toUpperCase();
        if (!currency.matches("[A-Z]{3}")) errors.add("통화는 KRW처럼 영문 3자리여야 합니다.");
        if (value.get("recipient_name").isBlank()) errors.add("수령인 이름이 비어 있습니다.");
        if (value.get("postal_code").isBlank()) errors.add("우편번호가 비어 있습니다.");
        if (value.get("address_line1").isBlank()) errors.add("기본 주소가 비어 있습니다.");
        return new ValidatedRow(row, orderedAt, quantity, unitPrice, currency, List.copyOf(errors));
    }

    private OffsetDateTime parseDate(String raw, List<String> errors) {
        try { return OffsetDateTime.parse(raw); }
        catch (DateTimeParseException exception) {
            errors.add("주문일시는 ISO-8601 형식이어야 합니다. 예: 2026-08-02T10:30:00+09:00");
            return null;
        }
    }

    private Integer parsePositiveInteger(String raw, String label, List<String> errors) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            errors.add(label + "은 1 이상의 정수여야 합니다.");
            return null;
        }
    }

    private BigDecimal parsePositiveDecimal(String raw, String label, List<String> errors) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            errors.add(label + "는 0 이상의 숫자여야 합니다.");
            return null;
        }
    }

    private void insertRow(UUID jobId, ValidatedRow row) {
        Map<String, String> value = row.row().values();
        try {
            jdbcTemplate.update("""
                    INSERT INTO order_import_rows
                        (id, integration_job_id, line_number, source_order_id, ordered_at, sku_code,
                         quantity, unit_price, currency, recipient_name, postal_code, address_line1,
                         address_line2, validation_status, error_codes, raw_data)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """, UUID.randomUUID(), jobId, row.row().lineNumber(), emptyToNull(value.get("source_order_id")),
                    row.orderedAt(), emptyToNull(value.get("sku_code")), row.quantity(), row.unitPrice(),
                    emptyToNull(row.currency()), emptyToNull(value.get("recipient_name")),
                    emptyToNull(value.get("postal_code")), emptyToNull(value.get("address_line1")),
                    emptyToNull(value.get("address_line2")), row.valid() ? "VALID" : "INVALID",
                    String.join(" | ", row.errors()), objectMapper.writeValueAsString(value));
        } catch (JacksonException exception) {
            throw new IllegalStateException("주문 원본 데이터를 저장하지 못했습니다.", exception);
        }
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "orders.csv";
        return fileName.replace('\\', '/').substring(fileName.replace('\\', '/').lastIndexOf('/') + 1);
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private record ParsedRow(int lineNumber, Map<String, String> values) { }
    private record ValidatedRow(ParsedRow row, OffsetDateTime orderedAt, Integer quantity,
                                BigDecimal unitPrice, String currency, List<String> errors) {
        boolean valid() { return errors.isEmpty(); }
        OrderImportRowResponse response() {
            return new OrderImportRowResponse(row.lineNumber(), row.values().get("source_order_id"),
                    row.values().get("sku_code"), quantity, unitPrice, valid() ? "VALID" : "INVALID", errors);
        }
    }
}
