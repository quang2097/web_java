package com.supermarket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.domain.*;
import com.supermarket.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@AutoConfigureMockMvc
@Import(JwtAuthenticationTest.RoleProbes.class)
class JwtAuthenticationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired EmployeeRepository employees;
    @Autowired RevokedTokenRepository revoked;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;

    @BeforeEach void seed() {
        revoked.deleteAll();
        accounts.deleteAll();
        employees.deleteAll();
        var employee = new Employee();
        employee.employeeCode = "E001"; employee.fullName = "Test cashier";
        var account = new Account();
        account.accountName = "cashier"; account.passwordHash = encoder.encode("CorrectPassword123!");
        account.role = Role.STAFF; account.employee = employees.save(employee);
        accounts.save(account);
    }

    @Test void loginIssuesBearerTokenWithoutSessionAndLogoutRevokesIt() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(body("cashier", "CorrectPassword123!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.accountName").value("cashier"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        assertNull(result.getRequest().getSession(false));
        var token = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("STAFF"));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsWrongAndUnknownCredentialsWithSameResponse() throws Exception {
        var wrong = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body("cashier", "wrong")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        var unknown = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body("unknown", "wrong")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(wrong, unknown);
    }
    @Test void rejectsMalformedCredentials() throws Exception {
        for (String name : List.of("", "' OR 1=1 --")) {
            mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body(name, "password")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }
    @Test void disabledAccountCannotLoginAndExistingTokenStopsWorking() throws Exception {
        String token = login();
        var account = accounts.findByAccountName("cashier").orElseThrow();
        account.enabled = false; accounts.saveAndFlush(account);
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body("cashier", "CorrectPassword123!")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    @Test void enforcesAllThreePermissionLevelsAndInvalidatesOldRoleToken() throws Exception {
        String staff = login();
        probe("pos", staff, 200); probe("management", staff, 403); probe("admin", staff, 403);
        var account = accounts.findByAccountName("cashier").orElseThrow();
        account.role = Role.MANAGER; accounts.saveAndFlush(account);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + staff)).andExpect(status().isUnauthorized());
        String manager = login();
        probe("pos", manager, 200); probe("management", manager, 200); probe("admin", manager, 403);
        account = accounts.findByAccountName("cashier").orElseThrow();
        account.role = Role.ADMIN; accounts.saveAndFlush(account);
        String admin = login();
        probe("pos", admin, 200); probe("management", admin, 200); probe("admin", admin, 200);
    }
    @Test void rejectsMissingExpiredAndTamperedTokens() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String valid = login();
        String tampered = (valid.charAt(0) == 'a' ? "b" : "a") + valid.substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("supermarket").subject("cashier").id("expired")
                .issuedAt(now.minusSeconds(3600)).expiresAt(now.minusSeconds(120))
                .claim("roles", List.of("STAFF")).claim("accountVersion", "0").build();
        var expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }
    private String body(String name, String password) throws Exception {
        return json.writeValueAsString(Map.of("accountName", name, "password", password));
    }
    private String login() throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(body("cashier", "CorrectPassword123!"))).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }
    private void probe(String module, String token, int status) throws Exception {
        mvc.perform(get("/api/" + module + "/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().is(status));
    }
    // Only test routes; no placeholder business endpoints in the application.
    @RestController
    static class RoleProbes {
        @GetMapping({"/api/pos/probe", "/api/management/probe", "/api/admin/probe"})
        Map<String, Boolean> probe() { return Map.of("allowed", true); }
    }
}
