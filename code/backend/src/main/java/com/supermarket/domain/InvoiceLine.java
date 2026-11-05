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
}
