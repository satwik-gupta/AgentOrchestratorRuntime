package com.agentorchestrator.workflow;

import java.util.Map;

@FunctionalInterface
public interface TaskAction {
    TaskResult execute(Map<String, Object> input) throws Exception;
}
