package com.agentorchestrator.core;

public class StateTransitionException extends Exception {
    public StateTransitionException(String message) {
        super(message);
    }

    public StateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
