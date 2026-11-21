package com.supermarket.domain;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "password_requests")
public class PasswordRequest {
    public enum Status { PENDING, APPROVED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) public Account account;
    @Column(name = "full_name", nullable = false, length = 120) public String fullName;
    @Column(name = "employee_code", nullable = false, length = 32) public String employeeCode;
    @Column(length = 500) public String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Status status = Status.PENDING;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "handled_by") public Account handledBy;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
    @Column(name = "handled_at") public Instant handledAt;
}