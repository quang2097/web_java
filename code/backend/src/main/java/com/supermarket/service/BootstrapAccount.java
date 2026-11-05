package com.supermarket.service;
import com.supermarket.domain.*;
import com.supermarket.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAccount implements ApplicationRunner {
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
        if (accounts.findByAccountName(name).isPresent()) return;
        var employee = new Employee();
        employee.employeeCode = "BOOTSTRAP-ADMIN";
        employee.fullName = "Quản trị viên";
        var account = new Account();
        account.accountName = name;
        account.passwordHash = encoder.encode(password);
        account.role = Role.ADMIN;
        account.employee = employees.save(employee);
        accounts.save(account);
    }
}
