package com.agentorchestrator.vectorstore;

import com.agentorchestrator.core.ChatMessage;
import com.agentorchestrator.core.ChatRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

public final class InMemoryVectorStore {
    private final ConcurrentHashMap<String, VectorDocument> index = new ConcurrentHashMap<>();

    public void add(VectorDocument doc) {
        Objects.requireNonNull(doc, "doc");
        if (index.putIfAbsent(doc.id(), doc) != null) {
            throw new IllegalArgumentException("Document with id already exists: " + doc.id());
        }
    }

    public void upsert(VectorDocument doc) {
        Objects.requireNonNull(doc, "doc");
        index.put(doc.id(), doc);
    }

    public VectorDocument get(String id) {
        return index.get(id);
    }

    public VectorDocument remove(String id) {
        return index.remove(id);
    }

    public void clear() {
        index.clear();
    }

    public int count() {
        return index.size();
    }

    public List<VectorDocument> all() {
        return new ArrayList<>(index.values());
    }

    public static final class SearchResult {
        private final VectorDocument document;
        private final double score;

        public SearchResult(VectorDocument document, double score) {
            this.document = document;
            this.score = score;
        }

        public VectorDocument document() { return document; }
        public double score() { return score; }
    }

    /**
     * Linear scan KNN search using virtual threads to parallelize score computation.
     */
    public List<SearchResult> search(Embedding query, int k) {
        Objects.requireNonNull(query, "query");
        if (k <= 0) throw new IllegalArgumentException("k must be > 0");

        List<VectorDocument> docs = all();
        if (docs.isEmpty()) return Collections.emptyList();

        ConcurrentLinkedQueue<SearchResult> results = new ConcurrentLinkedQueue<>();

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(docs.size());
            for (VectorDocument d : docs) {
                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    double score = SimilarityMetrics.cosineSimilarity(query, d.embedding());
                    results.add(new SearchResult(d, score));
                }, exec);
                futures.add(f);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        List<SearchResult> out = new ArrayList<>(results);
        out.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        if (out.size() > k) return out.subList(0, k);
        return out;
    }

    /**
     * Convenience: return nearest documents as ChatMessages so the agent can ingest them as long-term memory.
     */
    public List<ChatMessage> retrieveAsChatMessages(Embedding query, int k) {
        List<SearchResult> res = search(query, k);
        List<ChatMessage> msgs = new ArrayList<>();
        for (SearchResult r : res) {
            VectorDocument d = r.document();
            Map<String, String> meta = d.metadata();
            String prefix = meta != null && meta.containsKey("source") ? "[" + meta.get("source") + "] " : "";
            ChatMessage cm = new ChatMessage(ChatRole.SYSTEM, prefix + d.text());
            msgs.add(cm);
        }
        return msgs;
    }
}
