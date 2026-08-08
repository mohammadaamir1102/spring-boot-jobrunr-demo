package com.aamir.controller;

import com.aamir.entity.Order;
import com.aamir.repo.OrderRepository;
import com.aamir.req.CreateOrderRequest;
import com.aamir.req.OrderConfirmationEmailJobRequest;
import com.aamir.req.OrderProcessingJobRequest;
import com.aamir.service.BulkPickListBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * REST entry point that shows the two ways JobRunr gets off the request thread:
 *
 *  1. FIRE-AND-FORGET  -> jobRequestScheduler.enqueue(...)      -> any worker picks it up "now"
 *  2. DELAYED          -> jobRequestScheduler.schedule(...)     -> any worker picks it up "later"
 *  3. FAN-OUT / BATCH  -> N x enqueue(...), then a continuation -> parallel pick-list generation
 *
 * The HTTP thread only writes the order to MySQL (the shared job store) — the response is
 * 202 Accepted while the heavy lifting happens on a BackgroundJobServer node.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final JobRequestScheduler jobRequestScheduler;
    private final BulkPickListBatchService bulkPickListBatchService;

    /**
     * POST /api/v1/orders
     * Persists the order, then hands the rest to JobRunr:
     *  - one OrderProcessingJob  (fire-and-forget, allocated as soon as a worker is free)
     *  - one ConfirmationEmailJob 10 minutes from now (delayed)
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = Order.builder()
                .customerName(request.customerName())
                .customerEmail(request.customerEmail())
                .sku(request.sku())
                .quantity(request.quantity())
                .warehouseCode(request.warehouseCode())
                .build();
        orderRepository.save(order);

        // 1) fire-and-forget — returns before ANY heavy work happens
        JobId processingJob = jobRequestScheduler.enqueue(new OrderProcessingJobRequest(order.getId()));

        // 2) delayed — confirmation e-mail 10 minutes from now
        JobId emailJob = jobRequestScheduler.schedule(
                OffsetDateTime.now().plusMinutes(10),
                new OrderConfirmationEmailJobRequest(order.getId(), order.getCustomerEmail()));

        return ResponseEntity.accepted()
                .body(new OrderResponse(order.getId(), processingJob, emailJob));
    }

    /**
     * POST /api/v1/orders/warehouses/{warehouseCode}/generate-pick-lists
     * Fans out one pick-list generation job PER order in that warehouse. Each of those
     * jobs is picked up by whichever worker node is free, so a 10k-order batch finishes
     * in parallel instead of serially on one pod.
     */
    @PostMapping("/orders/warehouses/{warehouseCode}/generate-pick-lists")
    public ResponseEntity<Void> generatePickLists(@PathVariable String warehouseCode) {
        bulkPickListBatchService.enqueuePickListBatchForWarehouse(warehouseCode);
        return ResponseEntity.accepted().build();
    }

    public record OrderResponse(Long orderId, JobId processingJobId, JobId emailJobId) {}
}