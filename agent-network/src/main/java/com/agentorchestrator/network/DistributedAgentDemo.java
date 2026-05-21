package com.agentorchestrator.network;

import com.agentorchestrator.core.AgentStateMachine;
import com.agentorchestrator.tools.ToolRegistry;
import com.agentorchestrator.tools.ToolDispatcher;
import com.agentorchestrator.tools.sample.CalculatorTool;
import com.agentorchestrator.vectorstore.InMemoryVectorStore;
import com.agentorchestrator.vectorstore.Embedding;
import com.agentorchestrator.vectorstore.VectorDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;

public final class DistributedAgentDemo {
    public static void main(String[] args) throws Exception {
        int port = 9001;

        // Tools
        ToolRegistry registry = new ToolRegistry();
        CalculatorTool calc = new CalculatorTool();
        registry.register(calc);

        // Agent state
        AgentStateMachine serverAsm = new AgentStateMachine();
        ToolDispatcher dispatcher = new ToolDispatcher(registry, serverAsm);

        // Vector store (unused heavily here, but created to show integration)
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        vectorStore.upsert(new VectorDocument("v1", "Hello world", Map.of("source", "demo"), new Embedding(new double[]{0.1,0.2,0.3,0.4})));

        // Start network server
        AgentNetworkServer server = new AgentNetworkServer(port, registry, dispatcher, vectorStore, serverAsm);
        server.start();
        System.out.println("Server started on port " + port);

        // Event broker and agents
        AgentEventBroker broker = new AgentEventBroker();
        broker.registerAgent("agentA");
        broker.registerAgent("agentB");
        broker.subscribe("agentB", "tasks");

        // agentB will handle tasks by invoking tool dispatcher
        broker.registerHandler("agentB", event -> {
            try {
                System.out.println("agentB received event: " + event.getPayload());
                Object toolObj = event.getPayload().get("tool");
                if (toolObj instanceof String) {
                    String toolName = (String) toolObj;
                    Object argsObj = event.getPayload().get("args");
                    Map<String, Object> argMap = (argsObj instanceof Map) ? (Map<String, Object>) argsObj : new HashMap<>();
                    ObjectNodeWrapper res = new ObjectNodeWrapper(dispatcher.dispatch(toolName, argMap));
                    System.out.println("agentB tool result: " + (res.hasError() ? res.errorMessage() : res.asMap()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // AgentA publishes a task
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool", "add");
        payload.put("args", Map.of("a", 4, "b", 6));
        broker.publish("tasks", new AgentEvent("tasks", "agentA", payload));

        // Allow time for broker delivery
        TimeUnit.SECONDS.sleep(1);

        // Use network client to call a tool remotely via HTTP
        AgentNetworkClient client = new AgentNetworkClient("localhost", port);
        String resp = client.callTool("add", Map.of("a", 10, "b", 32));
        System.out.println("Network tool call response: " + resp);

        // Demonstrate streaming using Flow API
        AgentStreamPublisher publisher = new AgentStreamPublisher();
        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            private Flow.Subscription s;
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.s = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                System.out.println("Stream token: " + item);
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println("Stream complete");
            }
        };

        publisher.subscribe(subscriber);
        publisher.publish("Hello");
        publisher.publish(" world");
        publisher.publish("! This is a streamed response.");
        TimeUnit.MILLISECONDS.sleep(200);
        publisher.complete();

        // Cleanup
        TimeUnit.SECONDS.sleep(1);
        server.stop(0);
        broker.shutdown();
        System.out.println("DistributedAgentDemo finished.");
    }

    // Reuse ObjectNodeWrapper logic from AgentNetworkServer
    private static final class ObjectNodeWrapper {
        private final Object node;
        ObjectNodeWrapper(Object node) { this.node = node; }
        boolean hasError() {
            if (node == null) return true;
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
