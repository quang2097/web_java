package com.supermarket.domain;
import jakarta.persistence.*;

@Entity @Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "account_name", nullable = false, unique = true, length = 64) public String accountName;
    @Column(name = "password_hash", nullable = false, length = 100) public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Role role;
    @Column(nullable = false) public boolean enabled = true;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true) public Employee employee;
    @Version public long version;
}
