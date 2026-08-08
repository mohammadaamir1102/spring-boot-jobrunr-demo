package com.aamir.repo;


import com.aamir.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByWarehouseCode(String warehouseCode);
}