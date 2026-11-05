package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "warehouses")
public class Warehouse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 32) public String code;
    @Column(nullable = false, length = 120) public String name;
}
