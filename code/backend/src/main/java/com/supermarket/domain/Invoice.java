package com.supermarket.domain;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "invoices")
public class Invoice {
    public enum Status { DRAFT, PAID, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) public Employee employee;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") public Customer customer;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Status status = Status.DRAFT;
    @Version public long version;
}
