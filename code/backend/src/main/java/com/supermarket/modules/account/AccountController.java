package com.supermarket.modules.account;

import com.supermarket.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                        @NotBlank @Size(max = 72) String newPassword,
                                        @NotBlank @Size(max = 72) String confirmPassword) {}
    public record ForgotPasswordRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String accountName,
                                        @NotBlank @Size(max = 120) String fullName,
                                        @NotBlank @Size(max = 32) String employeeCode,
                                        @Size(max = 500) String reason) {}
    public record HandleRequest(@Size(max = 72) String newPassword) {}
    public record CreateAccountRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String accountName,
                                       @NotBlank @Size(max = 72) String password,
                                       @NotBlank @Size(max = 120) String fullName,
                                       @NotBlank @Size(max = 32) String employeeCode,
                                       @NotNull Role role) {}
    public record EnabledRequest(boolean enabled) {}

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest r) {
        service.changePassword(jwt.getSubject(), r.currentPassword(), r.newPassword(), r.confirmPassword());
        return ResponseEntity.noContent().build();
    }

    /** Công khai: người quên mật khẩu gửi thông tin cá nhân để Quản lý/Admin cấp lại mật khẩu. */
    @PostMapping("/api/auth/password-requests")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest r) {
        service.createPasswordRequest(r.accountName(), r.fullName(), r.employeeCode(), r.reason());
        return ResponseEntity.accepted().body(java.util.Map.of("message",
                "Nếu thông tin chính xác, yêu cầu đã được gửi tới Quản lý/Quản trị viên. Vui lòng liên hệ để nhận mật khẩu mới."));
    }

    @GetMapping("/api/management/password-requests")
    public List<AccountService.RequestView> requests(@AuthenticationPrincipal Jwt jwt) {
        return service.listRequests(jwt.getSubject());
    }
    @PostMapping("/api/management/password-requests/{id}/approve")
    public AccountService.RequestView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody HandleRequest r) {
        return service.handleRequest(jwt.getSubject(), id, true, r.newPassword());
    }
    @PostMapping("/api/management/password-requests/{id}/reject")
    public AccountService.RequestView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.handleRequest(jwt.getSubject(), id, false, null);
    }

    @GetMapping("/api/management/accounts")
    public List<AccountService.AccountView> accounts(@AuthenticationPrincipal Jwt jwt) {
        return service.listAccounts(jwt.getSubject());
    }
    @PostMapping("/api/management/accounts")
    public AccountService.AccountView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest r) {
        return service.createAccount(jwt.getSubject(), r.accountName(), r.password(), r.fullName(), r.employeeCode(), r.role());
    }
    @PutMapping("/api/management/accounts/{id}/enabled")
    public AccountService.AccountView enabled(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody EnabledRequest r) {
        return service.setEnabled(jwt.getSubject(), id, r.enabled());
    }
}