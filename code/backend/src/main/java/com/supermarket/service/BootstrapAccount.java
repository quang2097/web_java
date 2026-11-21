package com.supermarket.service;
import com.supermarket.domain.*;
import com.supermarket.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component @org.springframework.core.annotation.Order(1)
public class BootstrapAccount implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAccount.class);
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;
    private final Environment env;
    public BootstrapAccount(AccountRepository accounts, EmployeeRepository employees, PasswordEncoder encoder, Environment env) {
        this.accounts = accounts; this.employees = employees; this.encoder = encoder; this.env = env;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        String name = env.getProperty("APP_ADMIN_USERNAME");
        String password = env.getProperty("APP_ADMIN_PASSWORD");
        if (name == null && password == null) return;
        if (name == null || password == null || !name.matches("[A-Za-z0-9_.-]{3,64}")
                || password.length() < 12 || password.length() > 72
                || password.chars().anyMatch(c -> c < 32 || c > 126))
            throw new IllegalArgumentException("Supply an ASCII username (3–64) and printable ASCII password (12–72).");
        var existing = accounts.findByAccountName(name);
        if (existing.isPresent()) {
            var account = existing.get();
            if (env.getProperty("APP_ADMIN_RESET_PASSWORD", Boolean.class, false)) {
                if (account.role != Role.ADMIN)
                    throw new IllegalStateException("Administrator password reset cannot modify a non-admin account.");
                account.passwordHash = encoder.encode(password);
                accounts.saveAndFlush(account); // @Version invalidates previously issued JWTs.
                log.info("Configured administrator password reset completed. Disabled status was preserved.");
            } else if (!encoder.matches(password, account.passwordHash)) {
                log.warn("Configured bootstrap credentials differ from the existing account. Its password was preserved. Use the launcher's -ResetAdminPassword option to apply the configured password intentionally.");
            }
            return;
        }
        var employee = new Employee();
        employee.employeeCode = "BOOTSTRAP-ADMIN";
        if (employees.existsByEmployeeCode(employee.employeeCode))
            employee.employeeCode = "ADMIN-" + java.util.UUID.randomUUID().toString().substring(0, 26);
        employee.fullName = "Quản trị viên";
        var account = new Account();
        account.accountName = name;
        account.passwordHash = encoder.encode(password);
        account.role = Role.ADMIN;
        account.employee = employees.save(employee);
        accounts.save(account);
    }
}
