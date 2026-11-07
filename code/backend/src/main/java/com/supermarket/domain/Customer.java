package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "full_name", nullable = false, length = 120) public String fullName;
    @Column(length = 32) public String phone;
}
