package com.agentorchestrator.core;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class AgentCoreVerificationMain {
    private static final Logger LOGGER = Logger.getLogger(AgentCoreVerificationMain.class.getName());

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting Agent Core verification...");

        // State machine concurrency test
        AgentStateMachine machine = new AgentStateMachine();

        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> f1 = executor.submit(() -> {
                try {
                    machine.transitionTo(AgentState.PLANNING);
                    machine.transitionTo(AgentState.EXECUTING_TOOL);
                    machine.transitionTo(AgentState.AWAITING_RESPONSE);
                    machine.transitionTo(AgentState.COMPLETED);
                } catch (Exception e) {
                    LOGGER.severe("State transition error: " + e.getMessage());
                    machine.forceSet(AgentState.ERROR);
                }
            });

            Future<?> f2 = executor.submit(() -> {
                try {
                    boolean ok = machine.tryTransition(AgentState.PLANNING);
                    LOGGER.info("Concurrent tryTransition to PLANNING returned: " + ok);
                } catch (Exception e) {
                    LOGGER.severe("Concurrent transition failed: " + e.getMessage());
                }
            });

            f1.get();
            f2.get();
        }

        LOGGER.info("Final state: " + machine.getState());

        // Memory summarization test
        SlidingWindowMemory memory = new SlidingWindowMemory(40);
        for (int i = 1; i <= 12; i++) {
            memory.addMessage(new ChatMessage(ChatRole.USER, "Message number " + i + " — consume tokens and test eviction."));
        }

        LOGGER.info("Memory tokens after additions: " + memory.getCurrentTokenCount());
        LOGGER.info("Memory snapshot size: " + memory.getMessagesSnapshot().size());

        // Serialization test
        String snapshot = SerializationEngine.snapshot(machine, memory);
        LOGGER.info("Serialized snapshot length: " + snapshot.length());

        SerializationEngine.Snapshot restored = SerializationEngine.restoreSnapshot(snapshot);
        LOGGER.info("Restored state: " + restored.getState());
        LOGGER.info("Restored message count: " + (restored.getMessages() == null ? 0 : restored.getMessages().size()));

        LOGGER.info("Agent Core verification complete.");
    }
}
