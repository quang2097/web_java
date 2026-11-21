package com.supermarket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.domain.*;
import com.supermarket.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@AutoConfigureMockMvc
class SupermarketWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired EmployeeRepository employees;
    @Autowired PasswordRequestRepository passwordRequests;
    @Autowired RevokedTokenRepository revoked;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void resetAccounts() {
        passwordRequests.deleteAll();
        revoked.deleteAll();
        accounts.deleteAll();
        // tạo 3 tài khoản ứng với 3 vai trò trong sơ đồ ca sử dụng
        saveAccount("admin", "AdminPass123!", "A001", "Quản trị viên", Role.ADMIN);
        saveAccount("manager", "ManagerPass123!", "M001", "Quản lý cửa hàng", Role.MANAGER);
        saveAccount("staff", "StaffPass123!", "S001", "Nhân viên thu ngân", Role.STAFF);
    }

    private void saveAccount(String username, String password, String code, String name, Role role) {
        var emp = employees.findAll().stream().filter(e -> e.employeeCode.equals(code)).findFirst().orElseGet(() -> {
            var e = new Employee();
            e.employeeCode = code; e.fullName = name;
            return employees.save(e);
        });
        var acc = new Account();
        acc.accountName = username;
        acc.passwordHash = encoder.encode(password);
        acc.role = role;
        acc.employee = emp;
        accounts.save(acc);
    }

    private String login(String username, String password) throws Exception {
        var body = json.writeValueAsString(Map.of("accountName", username, "password", password));
        var res = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("accessToken").asText();
    }

    @Test void fullCatalogImportPosQrPaymentReportAndAiFlow() throws Exception {
        String manager = login("manager", "ManagerPass123!");
        String staff = login("staff", "StaffPass123!");

        // 1. Quản lý tạo danh mục + sản phẩm + nhập kho
        var catRes = mvc.perform(post("/api/management/categories")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Đồ uống thử nghiệm"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long catId = json.readTree(catRes).get("id").asLong();

        String barcode = "8939990001112";
        var prodPayload = Map.of(
                "name", "Sữa hạt óc chó",
                "categoryId", catId,
                "active", true,
                "skus", List.of(Map.of("code", barcode, "variant", "Lốc 4 hộp", "unit", "lốc", "unitPrice", 35000))
        );
        mvc.perform(post("/api/management/products")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(prodPayload)))
                .andExpect(status().isNoContent());

        // Quét mã vạch ở quầy thu ngân (ban đầu tồn kho = 0)
        var skuJson = mvc.perform(get("/api/pos/skus/by-code/" + barcode)
                .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0))
                .andReturn().getResponse().getContentAsString();
        long skuId = json.readTree(skuJson).get("id").asLong();

        // Nhập kho 30 lốc
        var importPayload = Map.of(
                "note", "Nhập lô đầu tiên",
                "lines", List.of(Map.of("skuId", skuId, "quantity", 30, "unitCost", 26000))
        );
        mvc.perform(post("/api/management/imports")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(importPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(780000));

        // 2. Nhân viên thu ngân tạo hoá đơn 4 lốc (140,000đ) và thanh toán chuyển khoản QR
        var invRes = mvc.perform(post("/api/pos/invoices")
                .header("Authorization", "Bearer " + staff)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "lines", List.of(Map.of("skuId", skuId, "quantity", 4)),
                        "customerName", "Trần Thị B",
                        "customerPhone", "0912345678"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(140000))
                .andExpect(jsonPath("$.qrUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long invoiceId = json.readTree(invRes).get("id").asLong();

        mvc.perform(post("/api/pos/invoices/" + invoiceId + "/pay")
                .header("Authorization", "Bearer " + staff)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("method", "TRANSFER", "received", 140000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // Kiểm tra tồn kho đã giảm từ 30 xuống 26
        mvc.perform(get("/api/pos/skus/by-code/" + barcode)
                .header("Authorization", "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(26));

        // 3. Báo cáo doanh thu & trợ lý AI nội bộ phản hồi số liệu khớp
        mvc.perform(get("/api/management/reports/summary")
                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayInvoices").value(1))
                .andExpect(jsonPath("$.todayRevenue").value(140000));

        mvc.perform(post("/api/management/ai/chat")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("question", "Tổng quan doanh thu hôm nay và gợi ý nhập hàng?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LOCAL"))
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test void accountCreationAndPasswordResetRequestRespectRoleHierarchy() throws Exception {
        String admin = login("admin", "AdminPass123!");
        String manager = login("manager", "ManagerPass123!");

        // Quản lý KHÔNG được tạo tài khoản Quản lý khác (403)
        mvc.perform(post("/api/management/accounts")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "accountName", "manager2", "password", "ValidPass123!",
                        "fullName", "Quản lý 2", "employeeCode", "M002", "role", "MANAGER"))))
                .andExpect(status().isForbidden());

        // Quản lý tạo tài khoản Nhân viên thành công
        mvc.perform(post("/api/management/accounts")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "accountName", "staff2", "password", "ValidPass123!",
                        "fullName", "Nhân viên 2", "employeeCode", "S002", "role", "STAFF"))))
                .andExpect(status().isOk());

        // Nhân viên gửi yêu cầu quên mật khẩu (không cần đăng nhập)
        mvc.perform(post("/api/auth/password-requests")
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "accountName", "staff2",
                        "fullName", "Nhân viên 2",
                        "employeeCode", "S002",
                        "reason", "Quên mật khẩu"))))
                .andExpect(status().isAccepted());

        var reqList = mvc.perform(get("/api/management/password-requests")
                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long reqId = json.readTree(reqList).get(0).get("id").asLong();

        // Quản lý duyệt yêu cầu đổi mật khẩu của nhân viên
        mvc.perform(post("/api/management/password-requests/" + reqId + "/approve")
                .header("Authorization", "Bearer " + manager)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("newPassword", "ResetPass999!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Nhân viên đăng nhập bằng mật khẩu mới và tự đổi mật khẩu
        String staff2Token = login("staff2", "ResetPass999!");
        mvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + staff2Token)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "currentPassword", "ResetPass999!",
                        "newPassword", "SelfChanged888!",
                        "confirmPassword", "SelfChanged888!"))))
                .andExpect(status().isNoContent());

        // Token cũ bị vô hiệu ngay vì @Version của Account đã tăng
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staff2Token))
                .andExpect(status().isUnauthorized());

        // Admin tạo được tài khoản Quản lý
        mvc.perform(post("/api/management/accounts")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "accountName", "manager3", "password", "ValidPass123!",
                        "fullName", "Quản lý 3", "employeeCode", "M003", "role", "MANAGER"))))
                .andExpect(status().isOk());
    }
}

