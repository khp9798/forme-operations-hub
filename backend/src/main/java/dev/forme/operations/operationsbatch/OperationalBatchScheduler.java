package dev.forme.operations.operationsbatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "forme.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OperationalBatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(OperationalBatchScheduler.class);
    private final OperationalBatchService service;
    public OperationalBatchScheduler(OperationalBatchService service) { this.service = service; }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    void refreshDailySales() {
        try { service.run("DAILY_SALES_AGGREGATE", BatchTriggerType.SCHEDULED, "system-scheduler"); }
        catch (OperationalBatchConflictException error) { log.info("Scheduled batch skipped: {}", error.getMessage()); }
        catch (Exception error) { log.error("Scheduled batch could not start", error); }
    }
}
