package com.agentorchestrator.tools;

import com.agentorchestrator.core.AgentStateMachine;
import com.agentorchestrator.tools.sample.CalculatorTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public final class ToolPluginDemo {
    public static void main(String[] args) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        CalculatorTool calc = new CalculatorTool();
        registry.register(calc);

        AgentStateMachine asm = new AgentStateMachine();
        ToolDispatcher dispatcher = new ToolDispatcher(registry, asm);
        McpJsonRpcHandler handler = new McpJsonRpcHandler(registry, dispatcher);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        // List tools
        String listReq = "{\"jsonrpc\":\"2.0\",\"id\":\"list-1\",\"method\":\"tools/list\",\"params\":{}}";
        String listResp = handler.handleSingle(listReq);
        System.out.println("List response: " + listResp);

        // Call add
        String callAdd = "{\"jsonrpc\":\"2.0\",\"id\":\"call-1\",\"method\":\"tools/call\",\"params\":{\"name\":\"add\",\"args\":{\"a\":7,\"b\":13}}}";
        String addResp = handler.handleSingle(callAdd);
        System.out.println("Add response: " + addResp);

        // Call divide with wrong param to see error handling
        String callDiv = "{\"jsonrpc\":\"2.0\",\"id\":\"call-2\",\"method\":\"tools/call\",\"params\":{\"name\":\"divide\",\"args\":{\"numerator\":10,\"denominator\":0}}}";
        String divResp = handler.handleSingle(callDiv);
        System.out.println("Divide response: " + divResp);

        // Demonstrate stdin/stdout handler using the same requests concatenated
        String combined = listReq + "\n" + callAdd + "\n" + callDiv + "\n";
        byte[] inputBytes = combined.getBytes();
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(inputBytes);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        handler.handleStdIo(in, out);
        String outStr = out.toString();
        System.out.println("StdIO combined output:\n" + outStr);
    }
}
