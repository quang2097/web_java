package com.supermarket.service;
import com.supermarket.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryRepository inventory;
    public InventoryService(InventoryRepository inventory) { this.inventory = inventory; }

    // Checkout must call this inside the same transaction as its invoice writes.
    // @Version detects competing updates; the losing transaction rolls back.
    @Transactional
    public void decreaseStock(long inventoryId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        var stock = inventory.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found"));
        if (stock.quantity < quantity) throw new IllegalStateException("Insufficient stock");
        stock.quantity -= quantity;
        inventory.flush();
    }
}
