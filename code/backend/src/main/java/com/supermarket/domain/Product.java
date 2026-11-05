package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 160) public String name;
    @Column(length = 1000) public String description;
}
