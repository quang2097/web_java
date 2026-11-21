package com.supermarket.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "payments")
public class Payment {
    public enum Method { CASH, TRANSFER }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id", nullable = false, unique = true) public Invoice invoice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Method method;
    @Column(nullable = false, precision = 19, scale = 2) public BigDecimal amount;
    @Column(nullable = false, precision = 19, scale = 2) public BigDecimal received;
    @Column(name = "change_amount", nullable = false, precision = 19, scale = 2) public BigDecimal changeAmount = BigDecimal.ZERO;
    @Column(name = "transfer_ref", length = 64) public String transferRef;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
}