package com.backtestjournal.backend.auth;

import com.backtestjournal.backend.User.User;
import com.backtestjournal.backend.User.UserRepository;
import com.backtestjournal.backend.telegram.TelegramClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern BOT_USERNAME = Pattern.compile("[A-Za-z0-9_]{5,32}");

    private final LoginCodeRepository codes;
    private final UserRepository users;
    private final TelegramClient telegram;
    private final String botUsername;
    private final boolean botAvailable;
    private final byte[] codeSecret;
    private final Object botValidationLock = new Object();
    private volatile Instant botValidatedUntil = Instant.EPOCH;
    private volatile Instant botValidationRetryAfter = Instant.EPOCH;

    public AuthService(LoginCodeRepository codes, UserRepository users, TelegramClient telegram,
                       @Value("${telegram.bot.username:}") String botUsername,
                       @Value("${telegram.bot.token:}") String botToken,
                       @Value("${telegram.bot.polling-enabled:true}") boolean pollingEnabled,
                       @Value("${auth.code-secret:}") String configuredCodeSecret,
                       @Value("${auth.allow-ephemeral-secret:false}") boolean allowEphemeralSecret) {
        this.codes = codes;
        this.users = users;
        this.telegram = telegram;
        this.botUsername = botUsername == null ? "" : botUsername.strip().replaceFirst("^@", "");
        this.botAvailable = botToken != null && !botToken.isBlank() && pollingEnabled;
        this.codeSecret = codeSecret(configuredCodeSecret, allowEphemeralSecret);
    }

    /** مرحله ۱: ساخت لینک ورود */
    public Map<String, String> start() {
        requireAvailableBot();

        Instant now = Instant.now();
        codes.deleteByExpiresAtBefore(now);

        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LoginCode lc = new LoginCode();
        lc.setLinkToken(token);
        lc.setExpiresAt(now.plusSeconds(600)); // ۱۰ دقیقه برای زدن Start
        codes.save(lc);

        return Map.of("linkToken", token,
                "link", "https://t.me/" + botUsername + "?start=" + token);
    }

    /** مرحله ۲: کاربر در تلگرام Start زد */
    @Transactional
    public void onBotStart(String linkToken, long chatId, String username, String firstName) {
        if (linkToken == null || linkToken.isBlank()) {
            telegram.sendMessage(chatId, "این لینک ورود نامعتبر یا منقضی شده. دوباره از سایت درخواست بدهید.");
            return;
        }

        LoginCode lc = codes.findByLinkToken(linkToken).orElse(null);
        if (lc == null || lc.isUsed() || lc.getExpiresAt() == null
                || !lc.getExpiresAt().isAfter(Instant.now())) {
            telegram.sendMessage(chatId, "این لینک ورود نامعتبر یا منقضی شده. دوباره از سایت درخواست بدهید.");
            return;
        }
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        lc.setTelegramId(chatId); // در چت خصوصی، chat id همان شناسه کاربر است
        lc.setTelegramUsername(username);
        lc.setDisplayName(firstName);
        lc.setCodeHash(hash(code, linkToken));
        lc.setAttempts(0);
        lc.setExpiresAt(Instant.now().plusSeconds(300)); // ۵ دقیقه برای وارد کردن کد
        codes.save(lc);

        telegram.sendMessage(chatId, "کد ورود شما: " + code + "\nاین کد ۵ دقیقه اعتبار دارد.");
    }

    /** مرحله ۳: بررسی کدی که کاربر در سایت وارد کرده */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public User verify(String linkToken, String code) {
        if (linkToken == null || linkToken.isBlank()) {
            throw bad("درخواست ورود پیدا نشد");
        }

        LoginCode lc = codes.findByLinkToken(linkToken)
                .orElseThrow(() -> bad("درخواست ورود پیدا نشد"));

        if (lc.isUsed() || lc.getCodeHash() == null || lc.getExpiresAt() == null
                || !lc.getExpiresAt().isAfter(Instant.now())) {
            throw bad("کد منقضی شده یا هنوز در تلگرام Start نزده‌اید");
        }
        if (lc.getAttempts() >= 5) {
            throw bad("تعداد تلاش بیش از حد مجاز است");
        }
        lc.setAttempts(lc.getAttempts() + 1);
        codes.save(lc);

        boolean ok = code != null && MessageDigest.isEqual(
                hash(code.trim(), linkToken).getBytes(StandardCharsets.UTF_8),
                lc.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) throw bad("کد اشتباه است");

        if (lc.getTelegramId() == null) {
            throw bad("Telegram account was not linked");
        }

        lc.setUsed(true);
        codes.save(lc);

        User user = users.findByTelegramId(lc.getTelegramId()).orElseGet(User::new);
        user.setTelegramId(lc.getTelegramId());
        user.setTelegramUsername(lc.getTelegramUsername());
        user.setDisplayName(lc.getDisplayName());
        User savedUser = users.save(user);
        codes.delete(lc);
        return savedUser;
    }

    @Transactional(readOnly = true)
    public User findUser(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String hash(String code, String salt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] codeSecret(String configuredSecret, boolean allowEphemeralSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            byte[] secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException("auth.code-secret must contain at least 32 bytes");
            }
            return secret;
        }
        if (!allowEphemeralSecret) {
            throw new IllegalStateException("auth.code-secret must be configured");
        }
        byte[] generated = new byte[32];
        RANDOM.nextBytes(generated);
        return generated;
    }

    private void requireAvailableBot() {
        if (!BOT_USERNAME.matcher(botUsername).matches() || !botAvailable) {
            throw unavailable();
        }

        Instant now = Instant.now();
        if (botValidatedUntil.isAfter(now)) return;
        if (botValidationRetryAfter.isAfter(now)) throw unavailable();

        synchronized (botValidationLock) {
            Instant checkedAt = Instant.now();
            if (botValidatedUntil.isAfter(checkedAt)) return;
            if (botValidationRetryAfter.isAfter(checkedAt)) throw unavailable();
            try {
                if (!botUsername.equalsIgnoreCase(telegram.getBotUsername())) {
                    throw unavailable();
                }
            } catch (ResponseStatusException exception) {
                botValidationRetryAfter = Instant.now().plusSeconds(30);
                throw exception;
            } catch (Exception exception) {
                botValidationRetryAfter = Instant.now().plusSeconds(30);
                throw unavailable();
            }
            botValidationRetryAfter = Instant.EPOCH;
            botValidatedUntil = checkedAt.plusSeconds(300);
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Telegram login is not configured");
    }

    private ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
