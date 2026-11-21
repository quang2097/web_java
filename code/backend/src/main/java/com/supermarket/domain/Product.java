package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 160) public String name;
    @Column(length = 1000) public String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id") public Category category;
    @Column(name = "image_url", length = 500) public String imageUrl;
    @Column(nullable = false) public boolean active = true;
}