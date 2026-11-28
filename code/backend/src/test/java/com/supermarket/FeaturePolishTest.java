package com.supermarket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.domain.*;
import com.supermarket.repository.*;
import com.supermarket.modules.pos.PosService;
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
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:polish-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.payment.bank-id=", "app.payment.account-no=", "app.ai.gemini-api-key=",
    "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@AutoConfigureMockMvc
@Transactional
class FeaturePolishTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired EmployeeRepository employees;
    @Autowired ProductRepository products;
    @Autowired SkuRepository skus;
    @Autowired InventoryRepository inventory;
    @Autowired WarehouseRepository warehouses;
    @Autowired PaymentRepository payments;
    @Autowired PosService pos;
    @Autowired PasswordEncoder encoder;
    @Autowired jakarta.persistence.EntityManager entityManager;
    String token;
    Sku sku;
    Inventory row;

    @BeforeEach void fixture() throws Exception {
        var employee = new Employee();
        employee.employeeCode = "POLISH"; employee.fullName = "Test Cashier";
        employees.save(employee);
        var account = new Account();
        account.accountName = "polish-admin"; account.employee = employee; account.role = Role.ADMIN;
        account.passwordHash = encoder.encode("PolishPassword123!"); accounts.saveAndFlush(account);
        var login = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("accountName", account.accountName, "password", "PolishPassword123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + json.readTree(login).get("accessToken").asText();
        var warehouse = new Warehouse(); warehouse.code = "MAIN"; warehouse.name = "Store"; warehouses.save(warehouse);
        var product = new Product(); product.name = "Original product"; products.save(product);
        sku = new Sku(); sku.product = product; sku.code = "POLISH-001"; sku.variant = "Small"; sku.unit = "box";
        sku.unitPrice = new BigDecimal("10000"); skus.save(sku);
        row = new Inventory(); row.sku = sku; row.warehouse = warehouse; row.quantity = 20; inventory.saveAndFlush(row);
        entityManager.clear();
    }

    private long draft(int quantity) throws Exception {
        var response = mvc.perform(post("/api/pos/invoices").header("Authorization", token).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("lines", List.of(Map.of("skuId", sku.id, "quantity", quantity))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("Original product Small", json.readTree(response).get("lines").get(0).get("name").asText());
        entityManager.flush();
        entityManager.clear();
        return json.readTree(response).get("id").asLong();
    }

    @Test void repeatedPaymentDeductsStockOnceAndConflictingRetryFails() throws Exception {
        long id = draft(2);
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/pos/invoices/" + id + "/pay").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{\"method\":\"CASH\",\"received\":25000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.changeAmount").value(5000));
        assertEquals(18, inventory.findById(row.id).orElseThrow().quantity);
        assertEquals(1, payments.count());
        mvc.perform(post("/api/pos/invoices/" + id + "/pay").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{\"method\":\"CASH\",\"received\":30000}"))
                .andExpect(status().isConflict());
    }

    @Test void insufficientCashAndUnavailableTransferKeepDraftAndStock() throws Exception {
        long id = draft(2);
        mvc.perform(post("/api/pos/invoices/" + id + "/pay").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{\"method\":\"CASH\",\"received\":10000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pos/invoices/" + id + "/pay").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{\"method\":\"TRANSFER\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(20, row.quantity);
        assertEquals(Invoice.Status.DRAFT, pos.get(id).status());
        assertEquals(0, payments.count());
    }

    @Test void cancelledDraftCanBeRetriedButCannotBePaid() throws Exception {
        long id = draft(1);
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/pos/invoices/" + id + "/cancel").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/pos/invoices/" + id + "/pay").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{\"method\":\"CASH\",\"received\":10000}"))
                .andExpect(status().isConflict());
        assertEquals(20, row.quantity);
    }

    @Test void historicalReceiptSurvivesCatalogChanges() throws Exception {
        long id = draft(1);
        sku = skus.findById(sku.id).orElseThrow();
        sku.product.name = "Renamed product"; sku.code = "NEW-CODE"; sku.variant = "Large"; sku.unit = "case";
        sku.unitPrice = new BigDecimal("99999"); skus.flush();
        var line = pos.get(id).lines().get(0);
        assertEquals("POLISH-001", line.code());
        assertEquals("Original product Small", line.name());
        assertEquals("box", line.unit());
        assertEquals(0, new BigDecimal("10000").compareTo(line.unitPrice()));
    }

    @Test void otherWarehouseStockDoesNotInflatePosOrDuplicateForecast() throws Exception {
        var other = new Warehouse(); other.code = "OTHER"; other.name = "Remote"; warehouses.save(other);
        var remote = new Inventory(); remote.warehouse = other; remote.sku = sku; remote.quantity = 500; inventory.saveAndFlush(remote);
        mvc.perform(get("/api/pos/skus/by-code/" + sku.code).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(20));
        mvc.perform(get("/api/management/reports/forecast").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentStock").value(20));
    }

    @Test void malformedNestedInputsAreRejectedWithoutWriting() throws Exception {
        for (String body : List.of("{\"lines\":[null]}", "{\"lines\":[{}]}",
                "{\"lines\":[{\"skuId\":1,\"quantity\":-1}]}",
                "{\"lines\":[{\"skuId\":1,\"quantity\":1.5}]}", "{\"lines\":[]}")) {
            mvc.perform(post("/api/pos/invoices").header("Authorization", token).contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/management/imports").header("Authorization", token).contentType(APPLICATION_JSON)
                .content("{\"lines\":[null]}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/management/products").header("Authorization", token).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"skus\":[null]}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/management/skus/" + sku.id).header("Authorization", token).contentType(APPLICATION_JSON)
                .content("{\"code\":\"VALID\",\"unitPrice\":0.5}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/management/accounts/1/enabled").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/management/inventory/" + row.id + "/min-quantity").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
    }

    @Test void duplicateCartLinesCannotBypassQuantityLimit() throws Exception {
        var line = Map.of("skuId", sku.id, "quantity", 6000);
        mvc.perform(post("/api/pos/invoices").header("Authorization", token).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("lines", List.of(line, line)))))
                .andExpect(status().isBadRequest());
    }

    @Test void accountCreationUsesSamePasswordPolicyAsBootstrap() throws Exception {
        mvc.perform(post("/api/management/accounts").header("Authorization", token).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("accountName", "short-password", "password", "Only8chr",
                        "fullName", "Test", "employeeCode", "NEW", "role", "STAFF"))))
                .andExpect(status().isBadRequest());
    }
}
