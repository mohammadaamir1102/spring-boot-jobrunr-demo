package com.aamir.controller;

import com.aamir.req.MiddayDispatchReportJobRequest;
import com.aamir.req.MidnightInventoryReconciliationJobRequest;
import com.aamir.service.ErpSyncService;
import com.aamir.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OPS-ONLY endpoints:
 *
 * In JobRunr Pro you get a one-click "Trigger now" button and the
 * BackgroundJob.triggerRecurringJob(...) API. On the open-source edition the
 * equivalent "run this recurring job right now" is simply enqueueing a one-shot
 * job of the same work — which these endpoints do. So you can test a midnight
 * job at 3pm without waiting for the cron and without touching the dashboard.
 *
 * The recurring Job IDs match the ids declared in RecurringJobs.java.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobDemoController {

    private final JobRequestScheduler jobRequestScheduler;
    private final JobScheduler jobScheduler;

    /**
     * POST /api/v1/jobs/recurring/{recurringJobId}/trigger
     * e.g. /api/v1/jobs/recurring/midnight-inventory-reconciliation/trigger
     */
    @PostMapping("/recurring/{recurringJobId}/trigger")
    public ResponseEntity<Void> triggerRecurringJob(@PathVariable String recurringJobId) {
        switch (recurringJobId) {
            case "midnight-inventory-reconciliation" ->
                    jobRequestScheduler.enqueue(new MidnightInventoryReconciliationJobRequest());
            case "midday-dispatch-report" ->
                    jobRequestScheduler.enqueue(new MiddayDispatchReportJobRequest());
            case "erp-stock-sync" ->
                    // IocJobLambda: JobRunr resolves the ErpSyncService bean on the worker node
                    jobScheduler.enqueue(ErpSyncService::pushStockLevelsToErp);
            case "hourly-stock-threshold-check" ->
                    jobScheduler.enqueue(InventoryService::checkStockThresholds);
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.accepted().build();
    }
}