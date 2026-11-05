package com.supermarket;

import com.supermarket.domain.*;
import com.supermarket.repository.InventoryRepository;
import com.supermarket.service.InventoryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:inventory-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class InventoryConcurrencyTest {
    @PersistenceContext EntityManager em;
    @Autowired InventoryRepository inventory;
    @Autowired InventoryService service;
    @Autowired PlatformTransactionManager transactions;

    @Test void competingSalesCannotOverwriteStockOrOversell() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> {
            var product = new Product(); product.name = "Milk"; em.persist(product);
            var sku = new Sku(); sku.product = product; sku.code = "MILK-1L";
            sku.variant = "1 litre"; sku.unit = "carton"; sku.unitPrice = new BigDecimal("25000.00"); em.persist(sku);
            var warehouse = new Warehouse(); warehouse.code = "MAIN"; warehouse.name = "Main"; em.persist(warehouse);
            var stock = new Inventory(); stock.sku = sku; stock.warehouse = warehouse; stock.quantity = 5; em.persist(stock);
            return stock.id;
        });
        var ready = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> sale = () -> {
            try {
                tx.executeWithoutResult(status -> {
                    inventory.findById(id).orElseThrow(); // Both transactions read version 0.
                    ready.countDown();
                    try {
                        if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent read timed out");
                    } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                    service.decreaseStock(id, 4);
                });
                return true;
            } catch (OptimisticLockingFailureException expected) { return false; }
        };
        try {
            var first = pool.submit(sale); var second = pool.submit(sale);
            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, inventory.findById(id).orElseThrow().quantity);
            assertThrows(IllegalStateException.class, () -> service.decreaseStock(id, 2));
            assertThrows(IllegalArgumentException.class, () -> service.decreaseStock(id, -1));
        } finally { pool.shutdownNow(); }
    }
}
