package com.agentorchestrator.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ChatMessage {
    private final String id;
    private final ChatRole role;
    private final String content;
    private final Instant createdAt;
    private final int tokens;

    @JsonCreator
    public ChatMessage(
            @JsonProperty("id") String id,
            @JsonProperty("role") ChatRole role,
            @JsonProperty("content") String content,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("tokens") Integer tokens) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.role = Objects.requireNonNull(role, "role");
        this.content = Objects.requireNonNull(content, "content");
        this.createdAt = (createdAt == null) ? Instant.now() : createdAt;
        this.tokens = (tokens == null) ? approximateTokenCount(this.content) : tokens;
    }

    public ChatMessage(ChatRole role, String content) {
        this(null, role, content, null, null);
    }

    public String getId() {
        return id;
    }

    public ChatRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getTokens() {
        return tokens;
    }

    private static int approximateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / 4);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", role=" + role +
                ", tokens=" + tokens +
                ", createdAt=" + createdAt +
                ", content='" + (content.length() > 80 ? content.substring(0, 80) + "..." : content) + '\'' +
                '}';
    }
}
