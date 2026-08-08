package com.aamir.service;


import com.aamir.constant.OrderStatus;
import com.aamir.entity.Order;
import com.aamir.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Called FIRE-AND-FORGET the instant an order is placed (see OrderController).
     * Kept off the request thread so the customer-facing API responds instantly
     * (202 Accepted) while heavier validation/allocation happens in the background,
     * on any worker node in the cluster.
     */
    public void processNewOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        log.info("Processing new order {} for SKU {}", orderId, order.getSku());
        // simulate allocation / fraud-check / stock-hold logic
        order.setStatus(OrderStatus.PICK_LIST_GENERATED);
        orderRepository.save(order);
    }

    public void markDispatched(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
        order.setStatus(OrderStatus.DISPATCHED);
        order.setDispatchedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}
