package com.aamir.repo;

import com.aamir.constant.OrderStatus;
import com.aamir.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByDispatchedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Order> findByWarehouseCodeAndStatus(String warehouseCode, OrderStatus status);
}