package com.aamir.service;


import com.aamir.entity.InventoryItem;
import com.aamir.repo.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.context.JobContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final NotificationService notificationService;

    /**
     * Called by the midnight recurring job.
     * Compares system quantity vs physical (cycle-count) quantity per SKU
     * and auto-corrects drift, logging every adjustment.
     *
     * JobContext is auto-injected by JobRunr when it is the last parameter of a
     * job method, whether the job is enqueued as a lambda or via JobRequestHandler.
     * Anything written to jobContext.logger() shows up directly in the JobRunr
     * dashboard's job detail screen - very useful for long-running nightly batches.
     */
    public void reconcileInventory(JobContext jobContext) {
        List<InventoryItem> items = inventoryRepository.findAll();
        jobContext.logger().info("Starting reconciliation for %d SKUs".formatted(items.size()));

        int corrected = 0;
        for (InventoryItem item : items) {
            if (!item.getSystemQuantity().equals(item.getPhysicalQuantity())) {
                log.warn("Drift found for SKU {}: system={} physical={}",
                        item.getSku(), item.getSystemQuantity(), item.getPhysicalQuantity());
                item.setSystemQuantity(item.getPhysicalQuantity());
                inventoryRepository.save(item);
                corrected++;
            }
        }
        jobContext.logger().info("Reconciliation complete. %d SKUs corrected.".formatted(corrected));
    }

    /**
     * Called by the hourly recurring job to detect items below reorder threshold.
     */
    public void checkStockThresholds() {
        List<InventoryItem> lowStock = inventoryRepository.findAll().stream()
                .filter(i -> i.getSystemQuantity() <= i.getReorderThreshold())
                .toList();

        if (!lowStock.isEmpty()) {
            log.info("{} SKUs below reorder threshold", lowStock.size());
            notificationService.sendLowStockAlert(lowStock);
        }
    }
}
