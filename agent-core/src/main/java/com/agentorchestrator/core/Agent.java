package com.agentorchestrator.core;

import java.io.IOException;

public interface Agent {
    String id();
    AgentState getState();
    void start() throws Exception;
    void stop() throws Exception;
    String serializeState() throws IOException;
}
