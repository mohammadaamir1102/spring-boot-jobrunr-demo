package com.aamir.service;


import com.aamir.constant.OrderStatus;
import com.aamir.entity.Order;
import com.aamir.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.context.JobContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    /**
     * Called by the MIDDAY recurring job (cron "0 0 12 * * *").
     * Summarizes dispatch activity for the day so far, grouped per warehouse.
     */
    public void generateMiddayDispatchReport(JobContext jobContext) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Order> dispatchedToday = orderRepository.findByDispatchedAtBetween(startOfDay, now);
        jobContext.logger().info("%d orders dispatched so far today".formatted(dispatchedToday.size()));

        Map<String, Long> perWarehouse = dispatchedToday.stream()
                .collect(Collectors.groupingBy(Order::getWarehouseCode, Collectors.counting()));

        long pending = orderRepository.findByStatus(OrderStatus.PLACED).size();

        StringBuilder summary = new StringBuilder();
        summary.append("Dispatch Report — ").append(now).append("\n");
        perWarehouse.forEach((wh, count) -> summary.append("  ").append(wh).append(": ").append(count).append(" dispatched\n"));
        summary.append("  Pending orders (not yet dispatched): ").append(pending);

        notificationService.sendDispatchReport(summary.toString());
    }
}