package com.backtestjournal.backend.telegram;

import com.backtestjournal.backend.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "telegram.bot.polling-enabled", havingValue = "true", matchIfMissing = true)
public class TelegramPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);

    private final TelegramClient telegram;
    private final AuthService authService;
    private final boolean enabled;
    private long offset = 0;
    private long failedUpdateId = -1;
    private int failedAttempts = 0;

    public TelegramPoller(TelegramClient telegram, AuthService authService,
                          @Value("${telegram.bot.token}") String token) {
        this.telegram = telegram;
        this.authService = authService;
        this.enabled = token != null && !token.isBlank();
        if (!enabled) log.warn("توکن بات تنظیم نشده؛ بات غیرفعال است.");
    }

    @Scheduled(fixedDelay = 500)
    public void poll() {
        if (!enabled) return;
        try {
            for (Map<String, Object> update : telegram.getUpdates(offset)) {
                long updateId = ((Number) update.get("update_id")).longValue();
                try {
                    handle(update);
                    offset = updateId + 1;
                    failedUpdateId = -1;
                    failedAttempts = 0;
                } catch (Exception exception) {
                    failedAttempts = failedUpdateId == updateId ? failedAttempts + 1 : 1;
                    failedUpdateId = updateId;
                    if (failedAttempts >= 3) {
                        log.error("Skipping Telegram update {} after 3 failed attempts ({}).",
                                updateId, exception.getClass().getSimpleName());
                        offset = updateId + 1;
                        failedUpdateId = -1;
                        failedAttempts = 0;
                        continue;
                    }
                    throw exception;
                }
            }
        } catch (Exception e) {
            log.error("Telegram polling failed ({}); retrying.", e.getClass().getSimpleName());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(Map<String, Object> update) {
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        if (message == null || message.get("text") == null) return;

        String text = (String) message.get("text");
        Map<String, Object> from = (Map<String, Object>) message.get("from");
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (from == null || chat == null || chat.get("id") == null) return;

        long chatId = ((Number) chat.get("id")).longValue();

        if (text.startsWith("/start")) {
            if (!"private".equals(chat.get("type"))) {
                telegram.sendMessage(chatId, "برای دریافت امن کد ورود، بات را در یک گفتگوی خصوصی باز کنید.");
                return;
            }

            String[] parts = text.split(" ", 2);
            if (parts.length == 2) {
                authService.onBotStart(parts[1].trim(), chatId,
                        (String) from.get("username"), (String) from.get("first_name"));
            } else {
                telegram.sendMessage(chatId, "سلام! برای ورود، از داخل سایت روی «ورود با تلگرام» بزنید.");
            }
        }
    }
}
