package com.agentorchestrator.workflow;

import com.agentorchestrator.core.AgentState;
import com.agentorchestrator.core.AgentStateMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorkflowExecutor {
    private static final Logger LOGGER = Logger.getLogger(WorkflowExecutor.class.getName());

    private final WorkflowGraph graph;
    private final AgentStateMachine agentStateMachine;
    private final ExecutorService executor;
    private final Map<String, CompletableFuture<TaskResult>> futures = new ConcurrentHashMap<>();

    public WorkflowExecutor(WorkflowGraph graph, AgentStateMachine agentStateMachine) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.agentStateMachine = Objects.requireNonNull(agentStateMachine, "agentStateMachine");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public CompletableFuture<Void> execute() {
        try {
            graph.validateNoCycles();
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        try {
            agentStateMachine.transitionTo(AgentState.PLANNING);
        } catch (Exception e) {
            // best-effort: log and continue
            LOGGER.log(Level.WARNING, "Unable to transition to PLANNING: " + e.getMessage(), e);
        }

        List<CompletableFuture<TaskResult>> allFutures = new ArrayList<>();

        // schedule futures lazily using recursive construction
        for (var task : graph.getTasks()) {
            allFutures.add(getOrCreateFuture(task.getId()));
        }

        // When any task begins we consider EXECUTING_TOOL
        try {
            agentStateMachine.transitionTo(AgentState.EXECUTING_TOOL);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to transition to EXECUTING_TOOL: " + e.getMessage(), e);
        }

        return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    boolean anyFailure = futures.values().stream().map(CompletableFuture::join).anyMatch(r -> !r.isSuccess());
                    try {
                        if (ex != null || anyFailure) {
                            agentStateMachine.transitionTo(AgentState.ERROR);
                        } else {
                            agentStateMachine.transitionTo(AgentState.COMPLETED);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failed to set agent final state: " + e.getMessage(), e);
                    } finally {
                        shutdownExecutor();
                    }
                });
    }

    private CompletableFuture<TaskResult> getOrCreateFuture(String taskId) {
        return futures.computeIfAbsent(taskId, id -> createTaskFuture(id));
    }

    private CompletableFuture<TaskResult> createTaskFuture(String taskId) {
        WorkflowTask task = graph.getTask(taskId);
        if (task == null) {
            CompletableFuture<TaskResult> cf = new CompletableFuture<>();
            cf.complete(TaskResult.failure("Unknown task: " + taskId));
            return cf;
        }

        Set<String> deps = task.getDependencies();
        if (deps.isEmpty()) {
            return runTaskWithRetry(task);
        }

        List<CompletableFuture<TaskResult>> depFutures = new ArrayList<>();
        for (String depId : deps) {
            depFutures.add(getOrCreateFuture(depId));
        }

        CompletableFuture<Void> depsAll = CompletableFuture.allOf(depFutures.toArray(new CompletableFuture[0]));

        return depsAll.thenComposeAsync(v -> {
            // inspect dependency results
            for (var df : depFutures) {
                TaskResult r = df.join();
                if (!r.isSuccess()) {
                    task.setState(TaskState.SKIPPED);
                    return CompletableFuture.completedFuture(TaskResult.failure("Prerequisite failed for " + task.getId()));
                }
            }
            return runTaskWithRetry(task);
        }, executor);
    }

    private CompletableFuture<TaskResult> runTaskWithRetry(WorkflowTask task) {
        task.setState(TaskState.RUNNING);
        // assemble merged input from dependencies' outputs + task's input payload
        Map<String, Object> merged = new HashMap<>();
        merged.putAll(task.getInputPayload());
        for (String depId : task.getDependencies()) {
            CompletableFuture<TaskResult> depF = futures.get(depId);
            if (depF != null && depF.isDone()) {
                TaskResult depRes = depF.join();
                for (Map.Entry<String, Object> e : depRes.getOutput().entrySet()) {
                    merged.put(depId + ":" + e.getKey(), e.getValue());
                }
            }
        }

        CompletableFuture<TaskResult> exec = RetryHandler.executeWithRetry(task.getAction(), Collections.unmodifiableMap(merged), task.getRetryPolicy(), executor)
                .thenApply(res -> {
                    if (res.isSuccess()) {
                        task.getOutputPayload().putAll(res.getOutput());
                        task.setState(TaskState.SUCCESS);
                    } else {
                        task.setState(TaskState.FAILED);
                    }
                    return res;
                });

        futures.put(task.getId(), exec);
        return exec;
    }

    private void shutdownExecutor() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error shutting executor: " + e.getMessage(), e);
        }
    }
}
