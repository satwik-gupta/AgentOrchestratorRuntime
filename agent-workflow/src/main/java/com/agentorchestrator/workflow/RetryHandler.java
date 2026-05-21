package com.agentorchestrator.workflow;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RetryHandler {
    private static final Logger LOGGER = Logger.getLogger(RetryHandler.class.getName());
    private static final Random RNG = new Random();

    private RetryHandler() { }

    public static CompletableFuture<TaskResult> executeWithRetry(TaskAction action, Map<String, Object> input, RetryPolicy policy, ExecutorService executor) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(executor, "executor");

        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            long base = policy.getBaseDelayMillis();
            while (true) {
                try {
                    TaskResult r = action.execute(input);
                    if (r == null) {
                        throw new IllegalStateException("Action returned null TaskResult");
                    }
                    if (r.isSuccess()) {
                        return r;
                    }
                    // treat non-success result as failure to allow retry
                    String msg = r.getMessage() == null ? "action failure" : r.getMessage();
                    throw new RuntimeException(msg);
                } catch (Exception e) {
                    attempt++;
                    if (attempt > policy.getMaxRetries()) {
                        LOGGER.log(Level.WARNING, "Task failed after max retries: " + e.getMessage(), e);
                        return TaskResult.failure("Failed after retries: " + e.getMessage());
                    }
                    // exponential backoff with jitter
                    long exp = base * (1L << (attempt - 1));
                    double jitter = policy.getJitterFactor() * exp * RNG.nextDouble();
                    long sleepMs = Math.min(Long.MAX_VALUE, Math.max(0L, exp + (long) jitter));
                    LOGGER.info(String.format("Retry attempt %d after %dms due to: %s", attempt, sleepMs, e.getMessage()));
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return TaskResult.failure("Interrupted during retry backoff");
                    }
                }
            }
        }, executor);
    }
}
