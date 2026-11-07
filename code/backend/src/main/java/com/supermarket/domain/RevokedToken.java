package com.supermarket.domain;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "revoked_tokens")
public class RevokedToken {
    @Id @Column(length = 36) public String id;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    public RevokedToken() {}
    public RevokedToken(String id, Instant expiresAt) { this.id = id; this.expiresAt = expiresAt; }
}
