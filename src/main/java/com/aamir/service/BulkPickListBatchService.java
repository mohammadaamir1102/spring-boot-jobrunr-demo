package com.aamir.service;

import com.aamir.constant.OrderStatus;
import com.aamir.entity.Order;
import com.aamir.repo.OrderRepository;
import com.aamir.req.PickListGenerationJobRequest;
import com.aamir.req.PickListValidationJobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * BATCH / FAN-OUT pattern.
 *
 * Open-source JobRunr does not ship atomic "batch" primitives or true
 * event-driven job chaining (BatchRequest, .continueWith(...)) — those are
 * JobRunr PRO features. This class shows the equivalent pattern you CAN build
 * on the free/OSS edition:
 *
 *   1. Enqueue one PickListGenerationJobRequest PER order — JobRunr spreads
 *      these across every worker node in the cluster automatically, so a
 *      10,000-order batch finishes in parallel instead of serially.
 *   2. Schedule one PickListValidationJobRequest slightly in the future as a
 *      best-effort "continuation" — good enough for most WMS batch workflows
 *      where the validation step just needs to run "a bit after" the batch,
 *      not the millisecond every last child job finishes.
 *
 * If you're on JobRunr Pro, replace step 2 with a real dependent job via
 * JobBuilder.aJob().... .after(batchId, ...) which only fires once every
 * child job in the batch has actually succeeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkPickListBatchService {

    private final JobRequestScheduler jobRequestScheduler;
    private final OrderRepository orderRepository;

    public void enqueuePickListBatchForWarehouse(String warehouseCode) {
        List<Order> orders = orderRepository.findByWarehouseCodeAndStatus(warehouseCode, OrderStatus.PLACED);

        if (orders.isEmpty()) {
            log.info("No PLACED orders for warehouse {}, nothing to batch", warehouseCode);
            return;
        }

        log.info("Fanning out {} pick-list jobs for warehouse {}", orders.size(), warehouseCode);
        orders.forEach(order ->
                jobRequestScheduler.enqueue(new PickListGenerationJobRequest(order.getId())));

        // best-effort continuation, ~2 minutes later
        jobRequestScheduler.schedule(
                OffsetDateTime.now().plus(Duration.ofMinutes(2)),
                new PickListValidationJobRequest(warehouseCode, orders.size()));
    }
}
