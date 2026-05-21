package com.agentorchestrator.workflow;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class WorkflowTask {
    private final String id;
    private final Set<String> dependencies;
    private final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.PENDING);
    private final Map<String, Object> inputPayload = new ConcurrentHashMap<>();
    private final Map<String, Object> outputPayload = new ConcurrentHashMap<>();
    private final TaskAction action;
    private final RetryPolicy retryPolicy;

    public WorkflowTask(String id, Set<String> dependencies, TaskAction action, RetryPolicy retryPolicy, Map<String, Object> initialInput) {
        this.id = Objects.requireNonNull(id, "id");
        this.dependencies = (dependencies == null) ? Collections.emptySet() : Set.copyOf(dependencies);
        this.action = Objects.requireNonNull(action, "action");
        this.retryPolicy = (retryPolicy == null) ? RetryPolicy.noRetry() : retryPolicy;
        if (initialInput != null) {
            this.inputPayload.putAll(initialInput);
        }
    }

    public String getId() {
        return id;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }

    public TaskState getState() {
        return state.get();
    }

    public boolean compareAndSetState(TaskState expected, TaskState update) {
        return state.compareAndSet(expected, update);
    }

    public void setState(TaskState s) {
        state.set(s);
    }

    public Map<String, Object> getInputPayload() {
        return inputPayload;
    }

    public Map<String, Object> getOutputPayload() {
        return outputPayload;
    }

    public TaskAction getAction() {
        return action;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}
