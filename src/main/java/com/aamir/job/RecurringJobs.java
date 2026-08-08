package com.aamir.job;
import com.aamir.service.ErpSyncService;
import com.aamir.service.InventoryService;
import com.aamir.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.jobs.context.JobContext;
import org.springframework.stereotype.Component;

/**
 * All RECURRING jobs live here, declared with JobRunr's @Recurring annotation.
 * jobrunr-spring-boot-3-starter scans every Spring bean at startup for methods
 * annotated with @Recurring and automatically registers them with JobRunr's
 * RecurringJobScheduler — no manual scheduleRecurrently() calls needed, and no
 * @EnableScheduling / @Scheduled anywhere near these methods.
 *
 * WHY NOT @Scheduled + ShedLock?
 * ------------------------------
 * @Scheduled runs independently on every application instance. In a horizontally
 * scaled, distributed deployment (2, 5, 20 pods) that means the SAME cron logic
 * fires N times at the same wall-clock second — for a job that reconciles
 * inventory or hits an external ERP, that's a correctness bug, not just wasted
 * compute. The classic fix is @Scheduled + ShedLock, which adds an external lock
 * table/row (Mongo, Redis, JDBC) purely so N-1 nodes back off and let 1 node run.
 *
 * JobRunr's recurring jobs solve the exact same problem WITHOUT ShedLock:
 *   - Each @Recurring definition is stored as a single row in JobRunr's own
 *     `jobrunr_recurring_jobs` table (already required, since JobRunr needs a
 *     shared DB/Redis for ALL of its jobs anyway).
 *   - At every poll interval, JobRunr generates the next due occurrence and
 *     inserts it into `jobrunr_jobs` using the storage engine's native atomic
 *     write (a SQL UPDATE ... WHERE state='SCHEDULED' with optimistic locking,
 *     or a Redis MULTI/EXEC). Only one BackgroundJobServer instance in the
 *     cluster wins that race and claims the job for execution.
 *   - So: same distributed-safety guarantee as ShedLock, zero extra
 *     infrastructure, zero extra annotations, and you get retries + dashboard
 *     visibility for free on top.
 *
 * The only place @Scheduled is still fine in a distributed system is when
 * duplicate execution on every node is harmless (e.g. an in-memory cache
 * warmup, a local metrics heartbeat) — see HeartbeatTask for that pattern.
 */
@Component
@RequiredArgsConstructor
public class RecurringJobs {

    private final InventoryService inventoryService;
    private final ReportService reportService;
    private final ErpSyncService erpSyncService;

    /**
     * MIDNIGHT job — every day at 00:00, warehouse timezone.
     */
    @Recurring(id = "midnight-inventory-reconciliation", cron = "0 0 0 * * *", zoneId = "Asia/Kolkata")
    @Job(name = "Midnight inventory reconciliation", retries = 2)
    public void reconcileInventoryAtMidnight(JobContext jobContext) {
        inventoryService.reconcileInventory(jobContext);
    }

    /**
     * MIDDAY job — every day at 12:00, warehouse timezone.
     */
    @Recurring(id = "midday-dispatch-report", cron = "0 0 12 * * *", zoneId = "Asia/Kolkata")
    @Job(name = "Midday dispatch report", retries = 2)
    public void generateMiddayReport(JobContext jobContext) {
        reportService.generateMiddayDispatchReport(jobContext);
    }

    /**
     * HOURLY job — stock threshold monitoring, every hour on the hour.
     */
    @Recurring(id = "hourly-stock-threshold-check", cron = "0 0 * * * *", zoneId = "Asia/Kolkata")
    @Job(name = "Hourly stock threshold check", retries = 1)
    public void checkStockThresholds() {
        inventoryService.checkStockThresholds();
    }

    /**
     * Every 30 minutes — sync stock levels to the external ERP.
     * retries = 5 because external systems are unreliable; JobRunr backs off
     * automatically between attempts (see ErpSyncService javadoc).
     */
    @Recurring(id = "erp-stock-sync", cron = "0 */30 * * * *")
    @Job(name = "ERP stock level sync", retries = 5)
    public void syncStockToErp() {
        erpSyncService.pushStockLevelsToErp();
    }
}
