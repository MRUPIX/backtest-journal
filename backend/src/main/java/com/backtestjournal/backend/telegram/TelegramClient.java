package com.backtestjournal.backend.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class TelegramClient {

    private final RestClient pollingRest;
    private final RestClient apiRest;

    public TelegramClient(@Value("${telegram.bot.token}") String token) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory pollingFactory = new JdkClientHttpRequestFactory(http);
        pollingFactory.setReadTimeout(Duration.ofSeconds(40));
        JdkClientHttpRequestFactory apiFactory = new JdkClientHttpRequestFactory(http);
        apiFactory.setReadTimeout(Duration.ofSeconds(10));
        String baseUrl = "https://api.telegram.org/bot" + token;
        this.pollingRest = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(pollingFactory)
                .build();
        this.apiRest = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(apiFactory)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUpdates(long offset) {
        Map<?, ?> res = pollingRest.get()
                .uri("/getUpdates?timeout=20&offset={o}", offset)
                .retrieve()
                .body(Map.class);
        if (res == null || res.get("result") == null) return List.of();
        return (List<Map<String, Object>>) res.get("result");
    }

    @SuppressWarnings("unchecked")
    public String getBotUsername() {
        Map<String, Object> response = apiRest.get()
                .uri("/getMe")
                .retrieve()
                .body(Map.class);
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            throw new IllegalStateException("Telegram rejected the bot credentials");
        }
        Map<String, Object> bot = (Map<String, Object>) response.get("result");
        if (bot == null || bot.get("username") == null) {
            throw new IllegalStateException("Telegram did not return a bot username");
        }
        return bot.get("username").toString();
    }

    public void sendMessage(long chatId, String text) {
        apiRest.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
