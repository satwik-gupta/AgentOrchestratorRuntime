package com.agentorchestrator.tools.sample;

import com.agentorchestrator.tools.Tool;
import com.agentorchestrator.tools.ToolParam;

public final class CalculatorTool {
    @Tool(name = "add", description = "Add two integers")
    public int add(@ToolParam(name = "a", description = "first integer") int a,
                   @ToolParam(name = "b", description = "second integer") int b) {
        return a + b;
    }

    @Tool(name = "divide", description = "Divide two numbers (may throw if divide by zero)")
    public double divide(@ToolParam(name = "numerator", description = "numerator") double numerator,
                         @ToolParam(name = "denominator", description = "denominator") double denominator) {
        if (denominator == 0.0) throw new IllegalArgumentException("denominator must not be zero");
        return numerator / denominator;
    }
}
