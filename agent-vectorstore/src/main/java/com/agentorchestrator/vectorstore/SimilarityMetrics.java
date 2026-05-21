package com.agentorchestrator.vectorstore;

import java.util.Objects;

public final class SimilarityMetrics {
    private SimilarityMetrics() { }

    /**
     * Compute exact cosine similarity between two embeddings.
     * Returns 0.0 if either vector is a zero-vector to avoid division-by-zero.
     */
    public static double cosineSimilarity(Embedding a, Embedding b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.dimension() != b.dimension()) {
            throw new IllegalArgumentException("Dimension mismatch: " + a.dimension() + " vs " + b.dimension());
        }

        double[] va = a.internalArray();
        double[] vb = b.internalArray();
        double dot = 0.0;
        double normA2 = 0.0;
        double normB2 = 0.0;
        for (int i = 0; i < va.length; i++) {
            double ai = va[i];
            double bi = vb[i];
            dot += ai * bi;
            normA2 += ai * ai;
            normB2 += bi * bi;
        }

        if (normA2 <= 0.0 || normB2 <= 0.0) return 0.0;
        double denom = Math.sqrt(normA2) * Math.sqrt(normB2);
        double res = dot / denom;
        if (Double.isNaN(res) || Double.isInfinite(res)) return 0.0;
        // clamp numerical noise
        if (res > 1.0) res = 1.0;
        if (res < -1.0) res = -1.0;
        return res;
    }
}
