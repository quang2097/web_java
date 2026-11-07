package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "employees")
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "employee_code", nullable = false, unique = true, length = 32) public String employeeCode;
    @Column(name = "full_name", nullable = false, length = 120) public String fullName;
}
