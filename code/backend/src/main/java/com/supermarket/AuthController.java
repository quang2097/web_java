package com.supermarket;

import com.supermarket.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String accountName,
            @NotBlank @Size(max = 72) @Pattern(regexp = "[\\x20-\\x7E]+") String password) {}

    @PostMapping("/login")
    public AuthService.LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.accountName(), request.password());
    }
    @GetMapping("/me")
    public AuthService.UserResponse me(@AuthenticationPrincipal Jwt jwt) { return auth.me(jwt.getSubject()); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(jwt);
        return ResponseEntity.noContent().build();
    }
}
