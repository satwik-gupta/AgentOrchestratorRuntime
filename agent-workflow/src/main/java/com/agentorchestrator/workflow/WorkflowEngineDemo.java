package com.agentorchestrator.workflow;

import com.agentorchestrator.core.AgentStateMachine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class WorkflowEngineDemo {
    private static final Logger LOGGER = Logger.getLogger(WorkflowEngineDemo.class.getName());

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting WorkflowEngineDemo...");

        WorkflowGraph graph = new WorkflowGraph();

        // Simulate task A (root)
        WorkflowTask A = new WorkflowTask("A", Set.of(), input -> {
            Thread.sleep(100);
            Map<String, Object> out = new HashMap<>();
            out.put("value", 5);
            LOGGER.info("A completed");
            return TaskResult.success(out, "A ok");
        }, new RetryPolicy(0, 0L, 0.0), null);

        // Task B depends on A; sometimes fails first to test retry
        AtomicInteger bAttempts = new AtomicInteger(0);
        WorkflowTask B = new WorkflowTask("B", Set.of("A"), input -> {
            int attempt = bAttempts.incrementAndGet();
            LOGGER.info("B attempt: " + attempt);
            int base = (int) input.getOrDefault("A:value", 0);
            if (attempt < 2) {
                throw new RuntimeException("Transient failure in B");
            }
            Map<String, Object> out = new HashMap<>();
            out.put("b", base + 1);
            return TaskResult.success(out, "B ok");
        }, new RetryPolicy(3, 100L, 0.5), null);

        // Task C depends on A
        WorkflowTask C = new WorkflowTask("C", Set.of("A"), input -> {
            Thread.sleep(50);
            int base = (int) input.getOrDefault("A:value", 0);
            Map<String, Object> out = new HashMap<>();
            out.put("c", base * 2);
            return TaskResult.success(out, "C ok");
        }, new RetryPolicy(0, 0L, 0.0), null);

        // Task D depends on B and C
        WorkflowTask D = new WorkflowTask("D", Set.of("B", "C"), input -> {
            int b = (int) input.getOrDefault("B:b", 0);
            int c = (int) input.getOrDefault("C:c", 0);
            Map<String, Object> out = new HashMap<>();
            out.put("d", b + c);
            return TaskResult.success(out, "D ok");
        }, new RetryPolicy(0, 0L, 0.0), null);

        // Task E depends on D
        WorkflowTask E = new WorkflowTask("E", Set.of("D"), input -> {
            int d = (int) input.getOrDefault("D:d", 0);
            Map<String, Object> out = new HashMap<>();
            out.put("e", d * 10);
            return TaskResult.success(out, "E ok");
        }, new RetryPolicy(0, 0L, 0.0), null);

        graph.addTask(A);
        graph.addTask(B);
        graph.addTask(C);
        graph.addTask(D);
        graph.addTask(E);

        AgentStateMachine asm = new AgentStateMachine();
        WorkflowExecutor executor = new WorkflowExecutor(graph, asm);

        CompletableFuture<Void> run = executor.execute();
        run.get(30, TimeUnit.SECONDS);

        LOGGER.info("Workflow finished. Agent final state: " + asm.getState());

        // Print outputs
        for (var t : List.of(A, B, C, D, E)) {
            LOGGER.info(() -> t.getId() + " state=" + t.getState() + " output=" + t.getOutputPayload());
        }

        LOGGER.info("WorkflowEngineDemo completed.");
    }
}
