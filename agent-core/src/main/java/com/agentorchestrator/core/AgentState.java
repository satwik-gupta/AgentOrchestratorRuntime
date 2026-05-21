package com.agentorchestrator.core;

public enum AgentState {
    IDLE,
    PLANNING,
    EXECUTING_TOOL,
    AWAITING_RESPONSE,
    COMPLETED,
    ERROR
}
