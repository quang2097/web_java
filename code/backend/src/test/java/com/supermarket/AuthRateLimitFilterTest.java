package com.supermarket;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {
    static class TestClock extends Clock {
        long millis;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    private MockHttpServletResponse request(AuthRateLimitFilter filter, String path, String ip) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test void limitsCredentialRequestsAndReleasesExpiredWindows() throws Exception {
        var clock = new TestClock();
        var filter = new AuthRateLimitFilter(clock);
        for (int i = 0; i < 30; i++) assertEquals(200, request(filter, "/api/auth/login", "127.0.0.1").getStatus());
        var rejected = request(filter, "/api/auth/login", "127.0.0.1");
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        assertEquals(200, request(filter, "/api/auth/login", "127.0.0.2").getStatus());
        assertEquals(200, request(filter, "/api/pos/invoices", "127.0.0.1").getStatus());
        clock.millis = 60000;
        assertEquals(200, request(filter, "/api/auth/login", "127.0.0.1").getStatus());
    }

    @Test void passwordRequestsHaveTheirOwnSmallerLimit() throws Exception {
        var filter = new AuthRateLimitFilter(new TestClock());
        for (int i = 0; i < 5; i++) assertEquals(200, request(filter, "/api/auth/password-requests", "127.0.0.1").getStatus());
        assertEquals(429, request(filter, "/api/auth/password-requests", "127.0.0.1").getStatus());
        assertEquals(200, request(filter, "/api/auth/login", "127.0.0.1").getStatus());
    }
}
