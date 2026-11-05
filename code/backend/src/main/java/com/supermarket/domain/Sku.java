package com.supermarket.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "skus")
public class Sku {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) public Product product;
    @Column(nullable = false, unique = true, length = 64) public String code;
    @Column(nullable = false, length = 120) public String variant;
    @Column(nullable = false, length = 32) public String unit;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2) public BigDecimal unitPrice;
}
