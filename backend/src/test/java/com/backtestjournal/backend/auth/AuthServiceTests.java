package com.backtestjournal.backend.auth;

import com.backtestjournal.backend.User.User;
import com.backtestjournal.backend.User.UserRepository;
import com.backtestjournal.backend.telegram.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("\\b\\d{6}\\b");
    private static final String CODE_SECRET = "unit-test-code-secret-that-is-long-enough";

    @Mock
    private LoginCodeRepository codes;

    @Mock
    private UserRepository users;

    @Mock
    private TelegramClient telegram;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(codes, users, telegram, "journal_bot",
                "bot-token", true, CODE_SECRET, false);
    }

    @Test
    void startCreatesExpiringTelegramLink() {
        Instant before = Instant.now();
        when(telegram.getBotUsername()).thenReturn("journal_bot");

        Map<String, String> result = authService.start();

        ArgumentCaptor<LoginCode> savedCode = ArgumentCaptor.forClass(LoginCode.class);
        verify(codes).save(savedCode.capture());
        LoginCode loginCode = savedCode.getValue();
        assertNotNull(loginCode.getExpiresAt());
        assertTrue(loginCode.getExpiresAt().isAfter(before.plusSeconds(590)));
        assertTrue(loginCode.getExpiresAt().isBefore(Instant.now().plusSeconds(610)));
        assertEquals(24, loginCode.getLinkToken().length());
        assertEquals(loginCode.getLinkToken(), result.get("linkToken"));
        assertEquals("https://t.me/journal_bot?start=" + loginCode.getLinkToken(), result.get("link"));
    }

    @Test
    void startRejectsMismatchedBotCredentials() {
        when(telegram.getBotUsername()).thenReturn("another_bot");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, authService::start);
        assertThrows(ResponseStatusException.class, authService::start);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(telegram).getBotUsername();
        verify(codes, never()).save(any());
    }

    @Test
    void onBotStartBindsTelegramAccountAndSendsHashedCode() {
        LoginCode loginCode = loginCode("link-token", Instant.now().plusSeconds(60));
        when(codes.findByLinkToken("link-token")).thenReturn(Optional.of(loginCode));

        authService.onBotStart("link-token", 42L, "trader", "Trader");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(42L), message.capture());
        Matcher matcher = SIX_DIGIT_CODE.matcher(message.getValue());
        assertTrue(matcher.find());
        assertEquals(hash(matcher.group(), "link-token"), loginCode.getCodeHash());
        assertEquals(42L, loginCode.getTelegramId());
        assertEquals("trader", loginCode.getTelegramUsername());
        assertEquals("Trader", loginCode.getDisplayName());
        assertEquals(0, loginCode.getAttempts());
        assertTrue(loginCode.getExpiresAt().isAfter(Instant.now().plusSeconds(290)));
        verify(codes).save(loginCode);
    }

    @Test
    void verifyCreatesUserAndConsumesValidCode() {
        LoginCode loginCode = loginCode("link-token", Instant.now().plusSeconds(60));
        loginCode.setCodeHash(hash("123456", "link-token"));
        loginCode.setTelegramId(42L);
        loginCode.setTelegramUsername("trader");
        loginCode.setDisplayName("Trader");
        when(codes.findByLinkToken("link-token")).thenReturn(Optional.of(loginCode));
        when(users.findByTelegramId(42L)).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.verify("link-token", " 123456 ");

        assertTrue(loginCode.isUsed());
        assertEquals(1, loginCode.getAttempts());
        assertEquals(42L, user.getTelegramId());
        assertEquals("trader", user.getTelegramUsername());
        assertEquals("Trader", user.getDisplayName());
    }

    @Test
    void verifyRejectsWrongCodeAndRecordsAttempt() {
        LoginCode loginCode = loginCode("link-token", Instant.now().plusSeconds(60));
        loginCode.setCodeHash(hash("123456", "link-token"));
        when(codes.findByLinkToken("link-token")).thenReturn(Optional.of(loginCode));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.verify("link-token", "654321"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(1, loginCode.getAttempts());
        assertFalse(loginCode.isUsed());
        verify(codes).save(loginCode);
        verify(users, never()).save(any());
    }

    private static LoginCode loginCode(String token, Instant expiresAt) {
        LoginCode loginCode = new LoginCode();
        loginCode.setLinkToken(token);
        loginCode.setExpiresAt(expiresAt);
        return loginCode;
    }

    private static String hash(String code, String salt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CODE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] value = mac.doFinal((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
