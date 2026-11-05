package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "inventory", uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "sku_id"}))
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "warehouse_id", nullable = false) public Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sku_id", nullable = false) public Sku sku;
    @Column(nullable = false) public int quantity;
    @Version public long version;
}
