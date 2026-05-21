package com.agentorchestrator.network;

import com.agentorchestrator.core.AgentStateMachine;
import com.agentorchestrator.workflow.WorkflowExecutor;
import com.agentorchestrator.workflow.WorkflowGraph;
import com.agentorchestrator.workflow.WorkflowTask;
import com.agentorchestrator.workflow.RetryPolicy;
import com.agentorchestrator.workflow.TaskResult;
import com.agentorchestrator.workflow.TaskAction;
import com.agentorchestrator.tools.ToolDispatcher;
import com.agentorchestrator.tools.ToolRegistry;
import com.agentorchestrator.vectorstore.InMemoryVectorStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public final class AgentNetworkServer {
    private final int port;
    private final ToolRegistry registry;
    private final ToolDispatcher dispatcher;
    private final InMemoryVectorStore vectorStore;
    private final AgentStateMachine agentStateMachine;
    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public AgentNetworkServer(int port, ToolRegistry registry, ToolDispatcher dispatcher, InMemoryVectorStore vectorStore, AgentStateMachine agentStateMachine) throws IOException {
        this.port = port;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.vectorStore = vectorStore;
        this.agentStateMachine = agentStateMachine;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        server.setExecutor(tpe);
        server.createContext("/tools/call", new ToolsCallHandler());
        server.createContext("/workflow/execute", new WorkflowExecuteHandler());
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    class ToolsCallHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = readAll(exchange.getRequestBody());
            JsonNode node = mapper.readTree(body);
            String name = node.path("name").asText(null);
            JsonNode argsNode = node.path("args");
            Map<String, Object> args = argsNode == null || argsNode.isNull() ? new HashMap<>() : mapper.convertValue(argsNode, new TypeReference<Map<String, Object>>(){});

            Object res = dispatcher.dispatch(name, args);
            String out = mapper.writeValueAsString(res);
            byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    class WorkflowExecuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = readAll(exchange.getRequestBody());
            JsonNode node = mapper.readTree(body);
            String toolName = node.path("toolName").asText(null);
            JsonNode argsNode = node.path("args");
            Map<String, Object> args = argsNode == null || argsNode.isNull() ? new HashMap<>() : mapper.convertValue(argsNode, new TypeReference<Map<String, Object>>(){});

            // construct a simple workflow that invokes the requested tool as a single task
            WorkflowGraph graph = new WorkflowGraph();
            TaskAction action = (input) -> {
                ObjectNodeWrapper resultNode = new ObjectNodeWrapper(dispatcher.dispatch(toolName, input));
                if (resultNode.hasError()) {
                    return TaskResult.failure(resultNode.errorMessage());
                }
                Map<String, Object> resultMap = resultNode.asMap();
                return TaskResult.success(resultMap);
            };

            WorkflowTask task = new WorkflowTask("invoke", java.util.Set.of(), action, new RetryPolicy(1, 50L, 0.2), args);
            graph.addTask(task);
            WorkflowExecutor executor = new WorkflowExecutor(graph, agentStateMachine);
            try {
                executor.execute().join();
            } catch (Exception e) {
                String err = "{\"error\":\"workflow execution failed: " + e.getMessage() + "\"}";
                byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            Map<String, Object> output = task.getOutputPayload();
            String out = mapper.writeValueAsString(output);
            byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

    private String readAll(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Small helper to interpret the dispatcher response object
    private static final class ObjectNodeWrapper {
        private final Object node;
        ObjectNodeWrapper(Object node) { this.node = node; }
        boolean hasError() {
            if (node == null) return true;
            // node may be com.fasterxml.jackson.databind.node.ObjectNode or other
            try {
                com.fasterxml.jackson.databind.JsonNode jn = (com.fasterxml.jackson.databind.JsonNode) node;
                return jn.has("error");
            } catch (ClassCastException e) {
                return false;
            }
        }
        String errorMessage() {
            try {
                com.fasterxml.jackson.databind.JsonNode jn = (com.fasterxml.jackson.databind.JsonNode) node;
                return jn.path("error").asText();
            } catch (ClassCastException e) {
                return null;
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap() {
            try {
                com.fasterxml.jackson.databind.JsonNode jn = (com.fasterxml.jackson.databind.JsonNode) node;
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
                if (jn.has("result")) {
                    return m.convertValue(jn.get("result"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
                }
                return m.convertValue(jn, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
            } catch (ClassCastException e) {
                Map<String, Object> map = new HashMap<>();
                map.put("result", node);
                return map;
            }
        }
    }
}
