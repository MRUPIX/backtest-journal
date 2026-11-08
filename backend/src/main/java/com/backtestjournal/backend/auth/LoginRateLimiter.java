package com.backtestjournal.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LoginRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String clientAddress) {
        long now = clock.millis();
        AtomicBoolean allowed = new AtomicBoolean();
        String key = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;

        windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= WINDOW_MILLIS) {
                allowed.set(true);
                return new Window(now, 1);
            }
            if (current.requests() < MAX_REQUESTS) {
                allowed.set(true);
                return new Window(current.startedAt(), current.requests() + 1);
            }
            return current;
        });

        if (windows.size() > 1_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }
        if (!allowed.get()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login requests. Try again in one minute.");
        }
    }

    private record Window(long startedAt, int requests) { }
}
