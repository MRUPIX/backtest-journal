package com.backtestjournal.backend.auth;

import com.backtestjournal.backend.User.UserRepository;
import com.backtestjournal.backend.telegram.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "telegram.bot.token=test-token",
        "telegram.bot.username=test_bot",
        "telegram.bot.polling-enabled=true",
        "auth.code-secret=integration-test-code-secret-that-is-long-enough"
})
class AuthServiceIntegrationTests {

    private static final Pattern CODE = Pattern.compile("\\b\\d{6}\\b");

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginCodeRepository codes;

    @Autowired
    private UserRepository users;

    @MockitoBean
    private TelegramClient telegram;

    @BeforeEach
    void cleanDatabase() {
        codes.deleteAll();
        users.deleteAll();
    }

    @Test
    void failedAttemptsCommitAndBlockTheSixthGuess() {
        when(telegram.getBotUsername()).thenReturn("test_bot");
        Map<String, String> login = authService.start();
        authService.onBotStart(login.get("linkToken"), 42L, "trader", "Trader");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(42L), message.capture());
        Matcher matcher = CODE.matcher(message.getValue());
        assertTrue(matcher.find());
        String wrongCode = matcher.group().equals("000000") ? "000001" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> authService.verify(login.get("linkToken"), wrongCode));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }

        assertThrows(ResponseStatusException.class,
                () -> authService.verify(login.get("linkToken"), wrongCode));
        LoginCode persisted = codes.findAll().getFirst();
        assertEquals(5, persisted.getAttempts());
    }
}
