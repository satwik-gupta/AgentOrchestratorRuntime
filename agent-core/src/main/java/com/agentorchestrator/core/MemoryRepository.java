package com.agentorchestrator.core;

import java.io.IOException;
import java.util.List;

public interface MemoryRepository {
    void addMessage(ChatMessage message) throws IOException;
    List<ChatMessage> getMessagesSnapshot();
    int getCurrentTokenCount();
    String serialize() throws IOException;
}
