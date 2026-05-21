package com.agentorchestrator.network;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AgentEvent {
    private final String id;
    private final String topic;
    private final String fromAgent;
    private final Map<String, Object> payload;
    private final Instant createdAt;

    public AgentEvent(String topic, String fromAgent, Map<String, Object> payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = Objects.requireNonNull(topic, "topic");
        this.fromAgent = Objects.requireNonNull(fromAgent, "fromAgent");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getFromAgent() { return fromAgent; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
