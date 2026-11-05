package com.supermarket.service;

import com.supermarket.domain.*;
import com.supermarket.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationManager authentication;
    private final AccountRepository accounts;
    private final RevokedTokenRepository revoked;
    private final JwtEncoder encoder;
    private final String issuer;
    private final long lifetime;
    public AuthService(AuthenticationManager authentication, AccountRepository accounts,
                       RevokedTokenRepository revoked, JwtEncoder encoder,
                       @Value("${app.jwt.issuer}") String issuer,
                       @Value("${app.jwt.lifetime-seconds}") long lifetime) {
        this.authentication = authentication; this.accounts = accounts; this.revoked = revoked;
        this.encoder = encoder; this.issuer = issuer; this.lifetime = lifetime;
    }
    public record UserResponse(String accountName, String fullName, List<String> roles) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}

    @Transactional(readOnly = true)
    public LoginResponse login(String name, String password) {
        var verified = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(name, password));
        var account = accounts.findByAccountName(verified.getName()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(account.accountName)
                .issuedAt(now).expiresAt(now.plusSeconds(lifetime)).id(UUID.randomUUID().toString())
                .claim("roles", List.of(account.role.name()))
                .claim("accountVersion", String.valueOf(account.version)).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new LoginResponse(token.getTokenValue(), "Bearer", lifetime, user(account));
    }
    @Transactional(readOnly = true)
    public UserResponse me(String name) {
        return user(accounts.findByAccountName(name).orElseThrow(() -> new BadCredentialsException("Invalid credentials")));
    }
    private UserResponse user(Account account) {
        return new UserResponse(account.accountName, account.employee.fullName, List.of(account.role.name()));
    }
    @Transactional
    public void logout(Jwt jwt) {
        revoked.save(new RevokedToken(jwt.getId(), jwt.getExpiresAt()));
    }
    @Scheduled(fixedDelay = 3600000) @Transactional
    public void cleanExpiredRevocations() { revoked.deleteByExpiresAtBefore(Instant.now()); }
}
