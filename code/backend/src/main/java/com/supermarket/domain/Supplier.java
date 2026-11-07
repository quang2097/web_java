package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "suppliers")
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 160) public String name;
    @Column(length = 32) public String phone;
    @Column(length = 300) public String address;
}