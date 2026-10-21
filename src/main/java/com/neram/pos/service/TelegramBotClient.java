package com.neram.pos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class TelegramBotClient {

    private final String botToken;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private String cachedBotUsername = null;
    private long lastUsernameFetch = 0;

    public TelegramBotClient(
            @Value("${neram.telegram.bot-token:}") String botToken,
            @Value("${neram.telegram.webhook-secret:}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.botToken = (botToken != null) ? botToken.trim() : "";
        this.webhookSecret = (webhookSecret != null) ? webhookSecret.trim() : "";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return !botToken.isBlank();
    }

    public boolean verifyWebhookSecret(String secretHeader) {
        if (webhookSecret.isBlank()) {
            return true; // No secret configured
        }
        return webhookSecret.equals(secretHeader);
    }

    public synchronized String getBotUsername() {
        if (!isConfigured()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (cachedBotUsername != null && (now - lastUsernameFetch) < 300000) { // cache 5 min
            return cachedBotUsername;
        }
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getMe";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.path("ok").asBoolean()) {
                    cachedBotUsername = root.path("result").path("username").asText(null);
                    lastUsernameFetch = now;
                    return cachedBotUsername;
                }
            }
            log.warn("Telegram getMe responded with HTTP {}", response.statusCode());
        } catch (Exception e) {
            log.warn("Failed to fetch Telegram bot username: {}", e.getMessage());
        }
        return cachedBotUsername;
    }

    public void sendMessage(String chatId, String message) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Telegram bot token is not configured on this server");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("Destination Telegram chat ID cannot be empty");
        }

        List<String> chunks = splitMessage(message, 3800);
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i);
            if (chunks.size() > 1) {
                text = String.format("<i>[Part %d/%d]</i>\n%s", i + 1, chunks.size(), text);
            }
            sendSingleChunk(chatId, text);
        }
    }

    private void sendSingleChunk(String chatId, String text) throws Exception {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId.trim());
        payload.put("text", text);
        payload.put("parse_mode", "HTML");
        payload.put("disable_web_page_preview", true);

        String json = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = "Telegram delivery failed with HTTP " + response.statusCode();
            try {
                JsonNode root = objectMapper.readTree(response.body());
                String desc = root.path("description").asText();
                if (desc != null && !desc.isBlank()) {
                    errorMsg = desc;
                }
            } catch (Exception ignored) {}
            throw new RuntimeException(errorMsg);
        }
    }

    public List<IncomingTelegramMessage> getUpdates(Long offset, int timeoutSeconds) {
        if (!isConfigured()) {
            return Collections.emptyList();
        }
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?timeout=" + timeoutSeconds;
            if (offset != null) {
                url += "&offset=" + offset;
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.path("ok").asBoolean()) {
                    List<IncomingTelegramMessage> list = new ArrayList<>();
                    JsonNode results = root.path("result");
                    if (results.isArray()) {
                        for (JsonNode node : results) {
                            IncomingTelegramMessage msg = parseUpdateNode(node);
                            if (msg != null) {
                                list.add(msg);
                            }
                        }
                    }
                    return list;
                }
            }
        } catch (Exception e) {
            log.debug("Telegram getUpdates check failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public IncomingTelegramMessage parseUpdateNode(JsonNode updateNode) {
        try {
            long updateId = updateNode.path("update_id").asLong();
            JsonNode message = updateNode.path("message");
            if (message.isMissingNode() || message.isNull()) {
                message = updateNode.path("channel_post");
            }
            if (message.isMissingNode() || message.isNull()) {
                return null;
            }

            String text = message.path("text").asText("");
            long chatId = message.path("chat").path("id").asLong();
            JsonNode from = message.path("from");
            String username = from.path("username").asText(null);
            String firstName = from.path("first_name").asText(null);

            return new IncomingTelegramMessage(updateId, chatId, text, username, firstName);
        } catch (Exception e) {
            log.warn("Error parsing Telegram update node: {}", e.getMessage());
            return null;
        }
    }

    public static List<String> splitMessage(String fullText, int maxChunkSize) {
        if (fullText == null || fullText.length() <= maxChunkSize) {
            return Collections.singletonList(fullText != null ? fullText : "");
        }
        List<String> chunks = new ArrayList<>();
        String[] lines = fullText.split("\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                if (line.length() > maxChunkSize) {
                    int start = 0;
                    while (start < line.length()) {
                        int end = Math.min(start + maxChunkSize, line.length());
                        chunks.add(line.substring(start, end));
                        start = end;
                    }
                    continue;
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(line);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    @lombok.Value
    public static class IncomingTelegramMessage {
        long updateId;
        long chatId;
        String text;
        String username;
        String firstName;
    }
}
