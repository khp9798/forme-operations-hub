package dev.forme.operations.audit;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public List<AuditLogResponse> search(String query, String action) {
        String keyword = query == null ? "" : query.trim();
        String actionValue = action == null ? "" : action.trim();
        return repository.search(keyword, actionValue);
    }
}
