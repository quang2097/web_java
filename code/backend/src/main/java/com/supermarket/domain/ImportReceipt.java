package com.supermarket.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "import_receipts")
public class ImportReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id") public Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "warehouse_id", nullable = false) public Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) public Employee employee;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
    @Column(length = 500) public String note;
    @Column(nullable = false, precision = 19, scale = 2) public BigDecimal total = BigDecimal.ZERO;
}