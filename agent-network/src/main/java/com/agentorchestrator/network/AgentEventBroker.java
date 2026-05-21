package com.agentorchestrator.network;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class AgentEventBroker {
    private final ConcurrentMap<String, BlockingQueue<AgentEvent>> mailboxes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArraySet<String>> topicSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<?>> handlerFutures = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void registerAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        mailboxes.computeIfAbsent(agentId, id -> new LinkedBlockingQueue<>());
    }

    public void subscribe(String agentId, String topic) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(topic, "topic");
        registerAgent(agentId);
        topicSubscribers.computeIfAbsent(topic, t -> new CopyOnWriteArraySet<>()).add(agentId);
    }

    public void unsubscribe(String agentId, String topic) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(topic, "topic");
        Set<String> s = topicSubscribers.get(topic);
        if (s != null) s.remove(agentId);
    }

    public void publish(String topic, AgentEvent event) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(event, "event");
        Set<String> subs = topicSubscribers.getOrDefault(topic, new CopyOnWriteArraySet<>());
        for (String sub : subs) {
            executor.submit(() -> {
                BlockingQueue<AgentEvent> q = mailboxes.get(sub);
                if (q != null) {
                    try {
                        q.put(event);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    public AgentEvent poll(String agentId, long timeoutMillis) throws InterruptedException {
        Objects.requireNonNull(agentId, "agentId");
        BlockingQueue<AgentEvent> q = mailboxes.get(agentId);
        if (q == null) return null;
        return q.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void registerHandler(String agentId, Consumer<AgentEvent> handler) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(handler, "handler");
        registerAgent(agentId);
        BlockingQueue<AgentEvent> q = mailboxes.get(agentId);
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    AgentEvent e = q.take();
                    try {
                        handler.accept(e);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, executor);
        handlerFutures.put(agentId, f);
    }

    public void unregisterHandler(String agentId) {
        CompletableFuture<?> f = handlerFutures.remove(agentId);
        if (f != null) f.cancel(true);
    }

    public void shutdown() {
        handlerFutures.values().forEach(f -> f.cancel(true));
        executor.shutdownNow();
        mailboxes.clear();
        topicSubscribers.clear();
    }
}
