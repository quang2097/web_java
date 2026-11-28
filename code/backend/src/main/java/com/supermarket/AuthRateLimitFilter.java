package com.supermarket;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded per-process protection for the public credential endpoints. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthRateLimitFilter() { this(Clock.systemUTC()); }
    AuthRateLimitFilter(Clock clock) { this.clock = clock; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !("/api/auth/login".equals(request.getServletPath())
                || "/api/auth/password-requests".equals(request.getServletPath()));
    }

    private synchronized long retryAfter(String key, int limit) {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= 60000);
        var window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10000) return 60;
            windows.put(key, new Window(now, 1));
            return 0;
        }
        if (window.count >= limit) return Math.max(1, (60000 - (now - window.start) + 999) / 1000);
        windows.put(key, new Window(window.start, window.count + 1));
        return 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Do not trust caller-supplied forwarding headers as a rate-limit identity.
        long retry = retryAfter(request.getRemoteAddr() + ":" + request.getServletPath(),
                request.getServletPath().endsWith("/login") ? 30 : 5);
        if (retry == 0) { chain.doFilter(request, response); return; }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"Quá nhiều yêu cầu. Vui lòng thử lại sau một phút.\"}");
    }
}
