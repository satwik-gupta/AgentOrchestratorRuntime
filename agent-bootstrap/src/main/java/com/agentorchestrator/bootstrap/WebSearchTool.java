package com.agentorchestrator.bootstrap;

import com.agentorchestrator.tools.Tool;
import com.agentorchestrator.tools.ToolParam;
import com.agentorchestrator.vectorstore.Embedding;
import com.agentorchestrator.vectorstore.InMemoryVectorStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple determinisitic "web search" implemented on top of the in-memory vector store.
 */
public final class WebSearchTool {
    private final InMemoryVectorStore store;

    public WebSearchTool(InMemoryVectorStore store) {
        this.store = store;
    }

    @Tool(name = "web_search", description = "Search the vector store for relevant passages")
    public List<Map<String,Object>> search(@ToolParam(name = "query") String query,
                                           @ToolParam(name = "k", required = false) Integer k) {
        int kk = (k == null) ? 5 : k;
        Embedding qEmb = embeddingFromText(query, 16);
        List<InMemoryVectorStore.SearchResult> res = store.search(qEmb, kk);
        List<Map<String,Object>> out = new ArrayList<>();
        for (InMemoryVectorStore.SearchResult r : res) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", r.document().id());
            m.put("text", r.document().text());
            m.put("score", r.score());
            m.put("metadata", r.document().metadata());
            out.add(m);
        }
        return out;
    }

    private Embedding embeddingFromText(String text, int dim) {
        if (text == null) text = "";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        double[] vec = new double[dim];
        for (int i = 0; i < bytes.length; i++) {
            int idx = i % dim;
            vec[idx] += (bytes[i] & 0xff) / 255.0;
        }
        // normalize
        double norm = 0.0;
        for (int i = 0; i < dim; i++) norm += vec[i] * vec[i];
        norm = Math.sqrt(Math.max(1e-12, norm));
        for (int i = 0; i < dim; i++) vec[i] = vec[i] / norm;
        return new Embedding(vec);
    }
}
