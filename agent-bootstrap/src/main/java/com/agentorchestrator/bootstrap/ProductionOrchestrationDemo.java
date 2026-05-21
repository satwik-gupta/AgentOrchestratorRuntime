package com.agentorchestrator.bootstrap;

import com.agentorchestrator.core.ChatMessage;
import com.agentorchestrator.core.ChatRole;
import com.agentorchestrator.network.AgentEvent;
import com.agentorchestrator.network.AgentEventBroker;
import com.agentorchestrator.network.AgentStreamPublisher;
import com.agentorchestrator.tools.ToolDispatcher;
import com.agentorchestrator.tools.ToolRegistry;
import com.agentorchestrator.vectorstore.Embedding;
import com.agentorchestrator.vectorstore.InMemoryVectorStore;
import com.agentorchestrator.vectorstore.VectorDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.agentorchestrator.workflow.*;

import java.util.concurrent.Flow;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.*;

/**
 * End-to-end integration demo: Researcher agent runs a workflow that executes a web-search tool
 * against a vector store and publishes findings; Writer agent consumes the findings and streams
 * a composed response using the LLM client.
 */
public final class ProductionOrchestrationDemo {
    public static void main(String[] args) throws Exception {
        String userCommand = (args != null && args.length > 0) ? args[0] : "Summarize recent advances in LLM evaluation.";

        InMemoryVectorStore store = new InMemoryVectorStore();
        seedStore(store);

        AgentEventBroker broker = new AgentEventBroker();
        ToolRegistry registry = new ToolRegistry();

        // Register web-search tool
        WebSearchTool web = new WebSearchTool(store);
        registry.register(web);

        // LLM client: prefer environment vars for provider
        String openaiKey = System.getenv("OPENAI_API_KEY");
        String base = System.getenv("LLM_BASE_URL");
        if (base == null || base.isBlank()) base = "https://api.openai.com";
        boolean openAiMode = (openaiKey != null && !openaiKey.isBlank());
        OllamaOrOpenAiClient client = new OllamaOrOpenAiClient(base, openaiKey, openAiMode);

        AgentEngineBootstrap engine = AgentEngineBootstrap.builder()
                .withVectorStore(store)
                .withBroker(broker)
                .withToolRegistry(registry)
                .withLLMClient(client)
                .build();

        // Create a tool dispatcher for the researcher
        ToolDispatcher researcherDispatcher = new ToolDispatcher(registry, new com.agentorchestrator.core.AgentStateMachine());

        // Writer: subscribe to findings
        broker.registerHandler("writer", event -> {
            try {
                Map<String,Object> payload = event.getPayload();
                ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
                List<Map<String,Object>> results = (List<Map<String,Object>>) payload.get("results");

                StringBuilder sb = new StringBuilder();
                sb.append("Research results for: ").append(payload.get("query")).append("\n\n");
                for (int i = 0; i < results.size(); i++) {
                    Map<String,Object> r = results.get(i);
                    sb.append(i+1).append(") ").append(r.get("text")).append(" (score=").append(r.get("score")).append(")\n");
                }

                List<ChatMessage> messages = new ArrayList<>();
                messages.add(new ChatMessage(ChatRole.SYSTEM, "You are an articulate technical writer who composes summaries from research findings."));
                messages.add(new ChatMessage(ChatRole.USER, "Use the findings below to write a concise summary and two actionable recommendations."));
                messages.add(new ChatMessage(ChatRole.AGENT, sb.toString()));

                AgentStreamPublisher pub = client.streamChatCompletion(messages, null, null, "gpt-4o-mini");
                CountDownLatch latch = new CountDownLatch(1);
                pub.subscribe(new Flow.Subscriber<>() {
                    private Flow.Subscription s;
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.s = subscription; subscription.request(Long.MAX_VALUE);
                    }
                    @Override
                    public void onNext(String item) { System.out.print(item); }
                    @Override
                    public void onError(Throwable throwable) { throwable.printStackTrace(); latch.countDown(); }
                    @Override
                    public void onComplete() { System.out.println("\n[Writer stream complete]"); latch.countDown(); }
                });

                // Wait for the writer stream to complete
                latch.await(2, TimeUnit.MINUTES);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Subscribe the writer handler to research findings so it receives published events
        broker.subscribe("writer", "research.findings");

        // Build a workflow graph that runs the researcher task
        WorkflowGraph graph = new WorkflowGraph();
        TaskAction researchAction = (input) -> {
            try {
                Map<String,Object> argsMap = new HashMap<>();
                argsMap.put("query", input.get("user_command"));
                argsMap.put("k", 3);
                ObjectNode res = researcherDispatcher.dispatch("web_search", argsMap);
                ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
                List<Map<String,Object>> hits = mapper.convertValue(res.get("result"), new TypeReference<List<Map<String,Object>>>(){});

                Map<String,Object> payload = new HashMap<>();
                payload.put("query", input.get("user_command"));
                payload.put("results", hits);
                broker.publish("research.findings", new AgentEvent("research.findings", "researcher", payload));
                return TaskResult.success(Map.of("published", true));
            } catch (Exception e) {
                return TaskResult.failure(e.getMessage());
            }
        };

        WorkflowTask researchTask = new WorkflowTask("research", Set.of(), researchAction, RetryPolicy.noRetry(), Map.of("user_command", userCommand));
        graph.addTask(researchTask);

        WorkflowExecutor executor = new WorkflowExecutor(graph, new com.agentorchestrator.core.AgentStateMachine());
        executor.execute().join();

        // allow some time for the writer to finish streaming
        Thread.sleep(500);

        // cleanup
        engine.close();
        client.close();
        broker.shutdown();
    }

    private static void seedStore(InMemoryVectorStore store) {
        // add a few deterministic documents
        store.clear();
        store.add(new VectorDocument("d1", "Recent progress in large language models has improved reasoning and alignment.", Map.of("source","papers:1"), deterministicEmbedding("Recent progress in large language models has improved reasoning and alignment.", 16)));
        store.add(new VectorDocument("d2", "Evaluation benchmarks now include multi-step reasoning and factuality tests.", Map.of("source","blog:2"), deterministicEmbedding("Evaluation benchmarks now include multi-step reasoning and factuality tests.", 16)));
        store.add(new VectorDocument("d3", "Tool-augmented agents combine LLMs with external APIs to improve grounded outputs.", Map.of("source","report:3"), deterministicEmbedding("Tool-augmented agents combine LLMs with external APIs to improve grounded outputs.", 16)));
    }

    private static Embedding deterministicEmbedding(String text, int dim) {
        byte[] b = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        double[] vec = new double[dim];
        for (int i = 0; i < b.length; i++) vec[i % dim] += (b[i] & 0xff) / 255.0;
        double s = 0.0;
        for (double v : vec) s += v * v;
        s = Math.sqrt(Math.max(1e-12, s));
        for (int i = 0; i < dim; i++) vec[i] /= s;
        return new Embedding(vec);
    }
}
