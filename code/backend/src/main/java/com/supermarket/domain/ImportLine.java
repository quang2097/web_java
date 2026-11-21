package com.supermarket.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "import_lines")
public class ImportLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id", nullable = false) public ImportReceipt receipt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sku_id", nullable = false) public Sku sku;
    @Column(nullable = false) public int quantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 2) public BigDecimal unitCost;
}