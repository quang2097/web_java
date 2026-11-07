package com.supermarket.modules.account;

import com.supermarket.common.ApiException;
import com.supermarket.domain.*;
import com.supermarket.repository.*;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ca sử dụng: Đổi mật khẩu, Quên mật khẩu / Tạo yêu cầu đổi mật khẩu, Xử lý yêu cầu đổi mật khẩu, Tạo tài khoản.
 * Quy tắc phân quyền: ADMIN quản lý mọi tài khoản; MANAGER chỉ quản lý tài khoản STAFF.
 * Mỗi lần đổi mật khẩu/khoá tài khoản, @Version của Account tăng nên mọi JWT cũ đều bị từ chối.
 */
@Service
public class AccountService {
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final PasswordRequestRepository requests;
    private final PasswordEncoder encoder;

    public AccountService(AccountRepository accounts, EmployeeRepository employees,
                          PasswordRequestRepository requests, PasswordEncoder encoder) {
        this.accounts = accounts; this.employees = employees; this.requests = requests; this.encoder = encoder;
    }

    public record AccountView(Long id, String accountName, String fullName, String employeeCode, Role role, boolean enabled) {
        static AccountView of(Account a) {
            return new AccountView(a.id, a.accountName, a.employee.fullName, a.employee.employeeCode, a.role, a.enabled);
        }
    }
    public record RequestView(Long id, String accountName, Role role, String fullName, String employeeCode, String reason,
                              PasswordRequest.Status status, String handledBy, Instant createdAt, Instant handledAt) {
        static RequestView of(PasswordRequest r) {
            return new RequestView(r.id, r.account.accountName, r.account.role, r.fullName, r.employeeCode, r.reason, r.status,
                    r.handledBy == null ? null : r.handledBy.accountName, r.createdAt, r.handledAt);
        }
    }

    static void checkPasswordPolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || password.chars().anyMatch(c -> c < 32 || c > 126))
            throw ApiException.badRequest("Mật khẩu mới phải dài 8–72 ký tự, chỉ gồm chữ không dấu, số và ký tự đặc biệt.");
    }

    private Account current(String accountName) {
        return accounts.findByAccountName(accountName).orElseThrow(() -> ApiException.forbidden("Tài khoản không tồn tại."));
    }

    private static boolean canManage(Account actor, Role target) {
        return actor.role == Role.ADMIN || (actor.role == Role.MANAGER && target == Role.STAFF);
    }

    // ---- Đổi mật khẩu (người dùng đã đăng nhập) ----
    @Transactional
    public void changePassword(String accountName, String currentPassword, String newPassword, String confirmPassword) {
        var account = current(accountName);
        if (!encoder.matches(currentPassword, account.passwordHash))
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng.");
        if (!newPassword.equals(confirmPassword))
            throw ApiException.badRequest("Xác nhận mật khẩu mới không khớp.");
        if (newPassword.equals(currentPassword))
            throw ApiException.badRequest("Mật khẩu mới phải khác mật khẩu hiện tại.");
        checkPasswordPolicy(newPassword);
        account.passwordHash = encoder.encode(newPassword);
    }

    // ---- Quên mật khẩu: gửi thông tin cá nhân để tạo yêu cầu (không cần đăng nhập) ----
    @Transactional
    public void createPasswordRequest(String accountName, String fullName, String employeeCode, String reason) {
        // Luôn trả về cùng một phản hồi để không lộ tài khoản nào tồn tại.
        accounts.findByAccountName(accountName.trim())
                .filter(a -> a.employee.fullName.trim().equalsIgnoreCase(fullName.trim())
                        && a.employee.employeeCode.trim().equalsIgnoreCase(employeeCode.trim()))
                .filter(a -> !requests.existsByAccountIdAndStatus(a.id, PasswordRequest.Status.PENDING))
                .ifPresent(a -> {
                    var r = new PasswordRequest();
                    r.account = a; r.fullName = fullName.trim(); r.employeeCode = employeeCode.trim();
                    r.reason = reason == null || reason.isBlank() ? null : reason.trim();
                    requests.save(r);
                });
    }

    // ---- Xử lý yêu cầu đổi mật khẩu (Quản lý: nhân viên; Admin: tất cả) ----
    @Transactional(readOnly = true)
    public List<RequestView> listRequests(String actorName) {
        var actor = current(actorName);
        return requests.findAllForReview().stream()
                .filter(r -> canManage(actor, r.account.role))
                .map(RequestView::of).toList();
    }

    @Transactional
    public RequestView handleRequest(String actorName, long id, boolean approve, String newPassword) {
        var actor = current(actorName);
        var r = requests.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu."));
        if (!canManage(actor, r.account.role)) throw ApiException.forbidden("Bạn không có quyền xử lý yêu cầu này.");
        if (r.status != PasswordRequest.Status.PENDING) throw ApiException.conflict("Yêu cầu đã được xử lý trước đó.");
        if (approve) {
            checkPasswordPolicy(newPassword);
            r.account.passwordHash = encoder.encode(newPassword);
            accounts.save(r.account);
        }
        r.status = approve ? PasswordRequest.Status.APPROVED : PasswordRequest.Status.REJECTED;
        r.handledBy = actor;
        r.handledAt = Instant.now();
        return RequestView.of(r);
    }

    // ---- Tạo / quản lý tài khoản ----
    @Transactional(readOnly = true)
    public List<AccountView> listAccounts(String actorName) {
        var actor = current(actorName);
        return accounts.findAllByOrderByIdAsc().stream()
                .filter(a -> canManage(actor, a.role) || a.id.equals(actor.id))
                .map(AccountView::of).toList();
    }

    @Transactional
    public AccountView createAccount(String actorName, String accountName, String password, String fullName,
                                     String employeeCode, Role role) {
        var actor = current(actorName);
        if (!canManage(actor, role))
            throw ApiException.forbidden(actor.role == Role.MANAGER
                    ? "Quản lý chỉ được tạo tài khoản nhân viên." : "Bạn không có quyền tạo tài khoản này.");
        if (accounts.findByAccountName(accountName).isPresent()) throw ApiException.conflict("Tên tài khoản đã tồn tại.");
        if (employees.existsByEmployeeCode(employeeCode.trim())) throw ApiException.conflict("Mã nhân viên đã tồn tại.");
        checkPasswordPolicy(password);
        var employee = new Employee();
        employee.employeeCode = employeeCode.trim();
        employee.fullName = fullName.trim();
        var account = new Account();
        account.accountName = accountName;
        account.passwordHash = encoder.encode(password);
        account.role = role;
        account.employee = employees.save(employee);
        return AccountView.of(accounts.save(account));
    }

    @Transactional
    public AccountView setEnabled(String actorName, long id, boolean enabled) {
        var actor = current(actorName);
        var account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản."));
        if (account.id.equals(actor.id)) throw ApiException.badRequest("Bạn không thể tự khoá tài khoản của mình.");
        if (!canManage(actor, account.role)) throw ApiException.forbidden("Bạn không có quyền thay đổi tài khoản này.");
        account.enabled = enabled;
        accounts.saveAndFlush(account);
        return AccountView.of(account);
    }
}