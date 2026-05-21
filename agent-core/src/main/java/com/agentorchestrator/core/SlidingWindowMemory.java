package com.agentorchestrator.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public final class SlidingWindowMemory implements MemoryRepository {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logger.getLogger(SlidingWindowMemory.class.getName());
    private final Deque<ChatMessage> messages = new LinkedList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxTokens;
    private int currentTokens = 0;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public SlidingWindowMemory(int maxTokens) {
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be > 0");
        this.maxTokens = maxTokens;
    }

    @Override
    public void addMessage(ChatMessage message) throws IOException {
        Objects.requireNonNull(message, "message");
        lock.lock();
        try {
            messages.addLast(message);
            currentTokens += message.getTokens();
            if (currentTokens > maxTokens) {
                List<ChatMessage> evicted = new ArrayList<>();
                while (!messages.isEmpty() && currentTokens > maxTokens / 2) {
                    ChatMessage removed = messages.removeFirst();
                    currentTokens -= removed.getTokens();
                    evicted.add(removed);
                }
                if (!evicted.isEmpty()) {
                    String summaryText = summarize(evicted);
                    ChatMessage summary = new ChatMessage(ChatRole.SUMMARY, summaryText);
                    messages.addFirst(summary);
                    currentTokens += summary.getTokens();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private String summarize(List<ChatMessage> evicted) {
        StringBuilder b = new StringBuilder();
        b.append("Summary:");
        for (ChatMessage m : evicted) {
            b.append(" [").append(m.getRole()).append("] ").append(truncate(m.getContent(), 200));
        }
        String res = b.toString();
        return res.length() > 1000 ? res.substring(0, 1000) : res;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public List<ChatMessage> getMessagesSnapshot() {
        lock.lock();
        try {
            return new ArrayList<>(messages);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getCurrentTokenCount() {
        lock.lock();
        try {
            return currentTokens;
        } finally {
            lock.unlock();
        }
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    @Override
    public String serialize() throws IOException {
        var snap = new java.util.HashMap<String, Object>();
        snap.put("maxTokens", maxTokens);
        snap.put("currentTokens", getCurrentTokenCount());
        snap.put("messages", getMessagesSnapshot());
        return mapper.writeValueAsString(snap);
    }

    public static SlidingWindowMemory deserialize(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var map = mapper.readValue(json, java.util.Map.class);
        Object mt = map.get("maxTokens");
        int maxTokens = (mt instanceof Number) ? ((Number) mt).intValue() : Integer.parseInt(mt.toString());
        SlidingWindowMemory mem = new SlidingWindowMemory(maxTokens);
        List<?> messages = (List<?>) map.get("messages");
        if (messages != null) {
            for (Object o : messages) {
                ChatMessage cm = mapper.convertValue(o, ChatMessage.class);
                mem.messages.addLast(cm);
                mem.currentTokens += cm.getTokens();
            }
        }
        return mem;
    }
}
