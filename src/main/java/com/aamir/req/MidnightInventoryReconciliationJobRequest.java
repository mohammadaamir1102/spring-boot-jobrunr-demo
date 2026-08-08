package com.aamir.req;


import com.aamir.service.InventoryService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

/**
 * RECURRING job — registered at 00:00 every night (see RecurringJobConfig).
 *
 * Why not @Scheduled(cron = "0 0 0 * * *") here?
 * Because we run MULTIPLE instances of this service behind a load balancer for
 * horizontal scale. Plain @Scheduled fires independently on EVERY node — you'd
 * get the reconciliation logic running N times concurrently, one per pod, which
 * for something that mutates inventory rows is actively dangerous (double
 * corrections, lock contention, duplicate alerts).
 *
 * The traditional fix is @Scheduled + ShedLock (a distributed lock in the DB/Redis
 * that only lets one node's @Scheduled execution proceed). JobRunr makes that
 * entire ShedLock layer unnecessary: recurring jobs are persisted as rows in
 * JobRunr's own `jobrunr_recurring_jobs` / `jobrunr_jobs` tables, and only ONE
 * BackgroundJobServer in the cluster claims (via a DB-level UPDATE ... WHERE state)
 * each due occurrence and executes it. No extra locking library, no extra
 * infrastructure — the job storage IS the lock.
 */
public record MidnightInventoryReconciliationJobRequest() implements JobRequest {

    @Override
    public Class<MidnightInventoryReconciliationJobRequestHandler> getJobRequestHandler() {
        return MidnightInventoryReconciliationJobRequestHandler.class;
    }

    @Component
    public static class MidnightInventoryReconciliationJobRequestHandler
            implements JobRequestHandler<MidnightInventoryReconciliationJobRequest> {


        private final InventoryService inventoryService;

        public MidnightInventoryReconciliationJobRequestHandler(InventoryService inventoryService) {
            this.inventoryService = inventoryService;
        }

        @Override
        @Job(name = "Midnight inventory reconciliation", retries = 2)
        public void run(MidnightInventoryReconciliationJobRequest jobRequest) {
            JobContext jobContext = jobContext();
            inventoryService.reconcileInventory(jobContext);
        }
    }
}
