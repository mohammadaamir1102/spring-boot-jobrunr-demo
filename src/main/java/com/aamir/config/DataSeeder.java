package com.aamir.config;

import com.aamir.constant.OrderStatus;
import com.aamir.entity.InventoryItem;
import com.aamir.entity.Order;
import com.aamir.repo.InventoryRepository;
import com.aamir.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo-only seed data so the recurring / array-out jobs have something to chew on.
 * Active only with the `demo` Spring profile (see README run commands).
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) {
        if (orderRepository.count() > 0 || inventoryRepository.count() > 0) {
            log.info("Database already has data, skipping demo seeding");
            return;
        }

        inventoryRepository.saveAll(List.of(
                InventoryItem.builder().sku("SKU-1001").warehouseCode("WH-BLR-01").systemQuantity(42).physicalQuantity(48).reorderThreshold(10).build(),
                InventoryItem.builder().sku("SKU-1002").warehouseCode("WH-BLR-01").systemQuantity(5).physicalQuantity(5).reorderThreshold(20).build(),
                InventoryItem.builder().sku("SKU-1003").warehouseCode("WH-DEL-01").systemQuantity(100).physicalQuantity(92).reorderThreshold(15).build()
        ));

        Order placed1 = Order.builder()
                .customerName("Aamir").customerEmail("aamir@example.com")
                .sku("SKU-1001").quantity(2).warehouseCode("WH-BLR-01").status(OrderStatus.PLACED)
                .createdAt(LocalDateTime.now().minusHours(2)).build();
        Order placed2 = Order.builder()
                .customerName("Riya").customerEmail("riya@example.com")
                .sku("SKU-1002").quantity(5).warehouseCode("WH-BLR-01").status(OrderStatus.PLACED)
                .createdAt(LocalDateTime.now().minusMinutes(40)).build();
        Order dispatched = Order.builder()
                .customerName("Kabir").customerEmail("kabir@example.com")
                .sku("SKU-1003").quantity(1).warehouseCode("WH-DEL-01").status(OrderStatus.DISPATCHED)
                .createdAt(LocalDateTime.now().minusHours(20)).dispatchedAt(LocalDateTime.now().minusHours(4)).build();
        orderRepository.saveAll(List.of(placed1, placed2, dispatched));

        log.info("Seeded demo data: 3 inventory items, {} orders (2 PLACED, 1 DISPATCHED). " +
                "Try POST /api/v1/orders and POST /api/v1/orders/warehouses/WH-BLR-01/generate-pick-lists", 3);
    }
}