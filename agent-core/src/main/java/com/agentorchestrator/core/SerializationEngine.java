package com.agentorchestrator.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public final class SerializationEngine {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SerializationEngine() { }

    public static String snapshot(AgentStateMachine stateMachine, SlidingWindowMemory memory) throws IOException {
        Snapshot s = new Snapshot(stateMachine.getState(), memory.getMessagesSnapshot(), memory.getMaxTokens(), Instant.now());
        return MAPPER.writeValueAsString(s);
    }

    public static Snapshot restoreSnapshot(String json) throws IOException {
        return MAPPER.readValue(json, Snapshot.class);
    }

    public static final class Snapshot {
        private final AgentState state;
        private final List<ChatMessage> messages;
        private final int maxTokens;
        private final Instant createdAt;

        @JsonCreator
        public Snapshot(
                @JsonProperty("state") AgentState state,
                @JsonProperty("messages") List<ChatMessage> messages,
                @JsonProperty("maxTokens") int maxTokens,
                @JsonProperty("createdAt") Instant createdAt) {
            this.state = state;
            this.messages = messages;
            this.maxTokens = maxTokens;
            this.createdAt = createdAt;
        }

        public AgentState getState() {
            return state;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}
