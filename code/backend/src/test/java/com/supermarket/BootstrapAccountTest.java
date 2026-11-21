package com.supermarket;

import com.supermarket.domain.*;
import com.supermarket.repository.*;
import com.supermarket.service.BootstrapAccount;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapAccountTest {
    final AccountRepository accounts = mock(AccountRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final MockEnvironment env = new MockEnvironment()
            .withProperty("APP_ADMIN_USERNAME", "admin")
            .withProperty("APP_ADMIN_PASSWORD", "NewPassword123!");
    Account existing;

    @BeforeEach void setup() {
        existing = new Account();
        existing.accountName = "admin"; existing.role = Role.ADMIN;
        existing.passwordHash = encoder.encode("OldPassword123!");
        when(accounts.findByAccountName("admin")).thenReturn(Optional.of(existing));
    }
    void bootstrap() {
        new BootstrapAccount(accounts, employees, encoder, env).run(new DefaultApplicationArguments());
    }
    @Test void normalStartupNeverResetsExistingPassword() {
        bootstrap();
        assertTrue(encoder.matches("OldPassword123!", existing.passwordHash));
        verify(accounts, never()).saveAndFlush(any());
    }
    @Test void explicitResetAppliesConfiguredPassword() {
        env.withProperty("APP_ADMIN_RESET_PASSWORD", "true");
        bootstrap();
        assertTrue(encoder.matches("NewPassword123!", existing.passwordHash));
        assertFalse(encoder.matches("OldPassword123!", existing.passwordHash));
        verify(accounts).saveAndFlush(existing);
    }
    @Test void resetDoesNotEnableDisabledAccount() {
        existing.enabled = false;
        env.withProperty("APP_ADMIN_RESET_PASSWORD", "true");
        bootstrap();
        assertFalse(existing.enabled);
    }
    @Test void resetCannotPromoteOrModifyStaffAccount() {
        existing.role = Role.STAFF;
        env.withProperty("APP_ADMIN_RESET_PASSWORD", "true");
        assertThrows(IllegalStateException.class, this::bootstrap);
        assertTrue(encoder.matches("OldPassword123!", existing.passwordHash));
        verify(accounts, never()).saveAndFlush(any());
    }
    @Test void newAdminAvoidsExistingBootstrapEmployeeCode() {
        when(accounts.findByAccountName("admin")).thenReturn(Optional.empty());
        when(employees.existsByEmployeeCode("BOOTSTRAP-ADMIN")).thenReturn(true);
        when(employees.save(any())).thenAnswer(call -> call.getArgument(0));
        bootstrap();
        var employee = org.mockito.ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(employee.capture());
        assertNotEquals("BOOTSTRAP-ADMIN", employee.getValue().employeeCode);
        assertTrue(employee.getValue().employeeCode.length() <= 32);
        var account = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(account.capture());
        assertEquals(Role.ADMIN, account.getValue().role);
        assertTrue(encoder.matches("NewPassword123!", account.getValue().passwordHash));
    }
}
