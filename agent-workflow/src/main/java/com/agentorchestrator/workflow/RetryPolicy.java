package com.agentorchestrator.workflow;

public final class RetryPolicy {
    private final int maxRetries;
    private final long baseDelayMillis;
    private final double jitterFactor;

    public RetryPolicy(int maxRetries, long baseDelayMillis, double jitterFactor) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries >= 0");
        if (baseDelayMillis < 0) throw new IllegalArgumentException("baseDelayMillis >= 0");
        if (jitterFactor < 0.0) throw new IllegalArgumentException("jitterFactor >= 0.0");
        this.maxRetries = maxRetries;
        this.baseDelayMillis = baseDelayMillis;
        this.jitterFactor = jitterFactor;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getBaseDelayMillis() {
        return baseDelayMillis;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0L, 0.0);
    }
}
