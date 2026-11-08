package com.backtestjournal.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTests {

    @Test
    void limitsEachClientToFiveStartsPerMinute() {
        LoginRateLimiter limiter = new LoginRateLimiter(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> limiter.check("127.0.0.1"));
        }

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> limiter.check("127.0.0.1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertDoesNotThrow(() -> limiter.check("127.0.0.2"));
    }
}
