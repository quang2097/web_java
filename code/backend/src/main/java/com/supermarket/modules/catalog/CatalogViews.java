package com.supermarket.modules.catalog;

import com.supermarket.domain.*;
import java.math.BigDecimal;
import java.util.List;

/** DTO trả về cho client (không lộ entity JPA ra ngoài). */
public final class CatalogViews {
    private CatalogViews() {}

    public record CategoryView(Long id, String name) {
        public static CategoryView of(Category c) { return new CategoryView(c.id, c.name); }
    }
    public record SkuView(Long id, String code, Long productId, String productName, String variant, String unit,
                          BigDecimal unitPrice, String categoryName, String imageUrl, long stock) {
        public static SkuView of(Sku s, long stock) {
            var p = s.product;
            return new SkuView(s.id, s.code, p.id, p.name, s.variant, s.unit, s.unitPrice,
                    p.category == null ? null : p.category.name, p.imageUrl, stock);
        }
        public String displayName() { return productName + (variant == null || variant.isBlank() ? "" : " " + variant); }
    }
    public record ProductView(Long id, String name, String description, Long categoryId, String categoryName,
                              String imageUrl, boolean active, List<SkuView> skus) {}
    public record SupplierView(Long id, String name, String phone, String address) {
        public static SupplierView of(Supplier s) { return new SupplierView(s.id, s.name, s.phone, s.address); }
    }
    public record WarehouseView(Long id, String code, String name) {
        public static WarehouseView of(Warehouse w) { return new WarehouseView(w.id, w.code, w.name); }
    }
}