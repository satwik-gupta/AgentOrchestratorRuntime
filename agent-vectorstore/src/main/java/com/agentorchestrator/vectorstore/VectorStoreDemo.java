package com.agentorchestrator.vectorstore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VectorStoreDemo {
    public static void main(String[] args) {
        InMemoryVectorStore store = new InMemoryVectorStore();

        // Hardcoded mock embeddings (dimension 4)
        double[][] vectors = new double[][]{
            {0.1, 0.2, 0.3, 0.4},
            {0.0, 0.2, 0.25, 0.5},
            {0.9, 0.1, 0.0, 0.0},
            {0.11, 0.19, 0.31, 0.39},
            {0.5, 0.4, 0.1, 0.0},
            {0.0, 0.0, 0.0, 0.0},
            {0.12, 0.21, 0.29, 0.38}
        };

        for (int i = 0; i < vectors.length; i++) {
            double[] v = vectors[i];
            Embedding emb = new Embedding(v);
            Map<String, String> meta = new HashMap<>();
            meta.put("source", "demo");
            VectorDocument doc = new VectorDocument("doc-" + (i + 1), "Document " + (i + 1) + " text.", meta, emb);
            store.upsert(doc);
        }

        // Query similar to doc-1 and doc-4
        Embedding query = new Embedding(new double[]{0.11, 0.2, 0.3, 0.4});

        List<InMemoryVectorStore.SearchResult> results = store.search(query, 5);

        System.out.println("Top results for query:");
        for (var r : results) {
            System.out.printf("id=%s score=%.6f text=%s\n", r.document().id(), r.score(), r.document().text());
        }

        // Show retrieval as ChatMessages
        var messages = store.retrieveAsChatMessages(query, 3);
        System.out.println("\nAs ChatMessages:");
        for (var m : messages) System.out.println(m.getRole() + ": " + m.getContent());
    }
}
