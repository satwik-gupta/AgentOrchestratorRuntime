package com.agentorchestrator.workflow;

import java.util.Collections;
import java.util.Map;

public final class TaskResult {
    private final boolean success;
    private final Map<String, Object> output;
    private final String message;

    private TaskResult(boolean success, Map<String, Object> output, String message) {
        this.success = success;
        this.output = (output == null) ? Collections.emptyMap() : Collections.unmodifiableMap(output);
        this.message = message;
    }

    public static TaskResult success(Map<String, Object> output) {
        return new TaskResult(true, output, "");
    }

    public static TaskResult success(Map<String, Object> output, String message) {
        return new TaskResult(true, output, message == null ? "" : message);
    }

    public static TaskResult failure(String message) {
        return new TaskResult(false, Collections.emptyMap(), message == null ? "" : message);
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public String getMessage() {
        return message;
    }
}
