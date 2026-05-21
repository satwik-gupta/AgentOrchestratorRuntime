package com.agentorchestrator.vectorstore;

import java.util.Map;
import java.util.Objects;

public record VectorDocument(String id, String text, Map<String, String> metadata, Embedding embedding) {
    public VectorDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(embedding, "embedding");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    }
}
