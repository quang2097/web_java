package com.supermarket.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "invoice_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "sku_id"}))
public class InvoiceLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id", nullable = false) public Invoice invoice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sku_id", nullable = false) public Sku sku;
    @Column(nullable = false) public int quantity;
    // Historical sale price, independent of subsequent SKU price changes.
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2) public BigDecimal unitPrice;
    @Column(name = "sku_code", length = 64) public String skuCode;
    @Column(name = "item_name", length = 281) public String itemName;
    @Column(name = "item_unit", length = 32) public String itemUnit;

    @PrePersist
    public void snapshot() {
        if (skuCode == null) skuCode = sku.code;
        if (itemName == null) itemName = (sku.product.name + " " + sku.variant).trim();
        if (itemUnit == null) itemUnit = sku.unit;
    }
}
