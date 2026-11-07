package com.supermarket.repository;
import com.supermarket.domain.Inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByWarehouseIdAndSkuId(Long warehouseId, Long skuId);

    @EntityGraph(attributePaths = {"warehouse", "sku", "sku.product", "sku.product.category"})
    @Query("select i from Inventory i order by i.sku.product.name, i.sku.variant")
    List<Inventory> findAllDetailed();

    @Query("select coalesce(sum(i.quantity), 0) from Inventory i where i.sku.id = :skuId")
    long totalQuantity(Long skuId);
}