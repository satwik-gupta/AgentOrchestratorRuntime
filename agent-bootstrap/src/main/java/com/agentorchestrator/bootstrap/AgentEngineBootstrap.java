package com.agentorchestrator.bootstrap;

import com.agentorchestrator.core.Agent;
import com.agentorchestrator.network.AgentEventBroker;
import com.agentorchestrator.tools.ToolDispatcher;
import com.agentorchestrator.tools.ToolRegistry;
import com.agentorchestrator.vectorstore.InMemoryVectorStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Fluent bootstrap for composing the agent runtime.
 */
public final class AgentEngineBootstrap implements AutoCloseable {
    private final InMemoryVectorStore vectorStore;
    private final AgentEventBroker broker;
    private final ToolRegistry toolRegistry;
    private final OllamaOrOpenAiClient llmClient;
    private final ExecutorService executor;
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    private AgentEngineBootstrap(InMemoryVectorStore vectorStore,
                                 AgentEventBroker broker,
                                 ToolRegistry toolRegistry,
                                 OllamaOrOpenAiClient llmClient,
                                 ExecutorService executor) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.broker = Objects.requireNonNull(broker, "broker");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.llmClient = llmClient; // optional
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private InMemoryVectorStore vectorStore;
        private AgentEventBroker broker;
        private ToolRegistry toolRegistry;
        private OllamaOrOpenAiClient llmClient;
        private ExecutorService executor;
        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        private final List<AgentProfile> loadedProfiles = new ArrayList<>();

        public Builder withVectorStore(InMemoryVectorStore vs) { this.vectorStore = vs; return this; }
        public Builder withBroker(AgentEventBroker b) { this.broker = b; return this; }
        public Builder withToolRegistry(ToolRegistry r) { this.toolRegistry = r; return this; }
        public Builder withLLMClient(OllamaOrOpenAiClient c) { this.llmClient = c; return this; }
        public Builder withExecutor(ExecutorService e) { this.executor = e; return this; }

        /**
         * Load simple agent profiles from a JSON file. The file is an array of objects with fields
         * { "id": "agentA", "name": "Agent A", "systemPrompt": "..." }
         */
        public Builder loadAgentsFromJson(Path path) throws IOException {
            Objects.requireNonNull(path, "path");
            byte[] b = Files.readAllBytes(path);
            List<AgentProfile> profiles = mapper.readValue(b, new TypeReference<>(){});
            loadedProfiles.addAll(profiles);
            return this;
        }

        public AgentEngineBootstrap build() {
            InMemoryVectorStore vs = (vectorStore == null) ? new InMemoryVectorStore() : vectorStore;
            AgentEventBroker b = (broker == null) ? new AgentEventBroker() : broker;
            ToolRegistry tr = (toolRegistry == null) ? new ToolRegistry() : toolRegistry;
            ExecutorService ex = (executor == null) ? Executors.newVirtualThreadPerTaskExecutor() : executor;
            return new AgentEngineBootstrap(vs, b, tr, llmClient, ex);
        }
    }

    public void registerAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent");
        agents.put(agent.id(), agent);
        try {
            broker.registerAgent(agent.id());
        } catch (Exception ignored) { }
    }

    public void registerTool(Object tool) {
        Objects.requireNonNull(tool, "tool");
        toolRegistry.register(tool);
    }

    public ToolDispatcher createToolDispatcherForAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        return new ToolDispatcher(toolRegistry, new com.agentorchestrator.core.AgentStateMachine());
    }

    public InMemoryVectorStore getVectorStore() { return vectorStore; }
    public AgentEventBroker getBroker() { return broker; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public OllamaOrOpenAiClient getLlmClient() { return llmClient; }

    public void startAll() throws Exception {
        for (Agent a : agents.values()) a.start();
    }

    public void stopAll() throws Exception {
        for (Agent a : agents.values()) a.stop();
    }

    @Override
    public void close() throws Exception {
        try {
            stopAll();
        } catch (Exception ignore) { }
        try {
            if (llmClient != null) llmClient.close();
        } catch (Exception ignore) { }
        try {
            broker.shutdown();
        } catch (Exception ignore) { }
        try {
            executor.shutdownNow();
        } catch (Exception ignore) { }
    }

    public List<String> registeredAgentIds() { return Collections.unmodifiableList(new ArrayList<>(agents.keySet())); }

    /**
     * Small DTO for agent profile import
     */
    public static final class AgentProfile {
        public String id;
        public String name;
        public String systemPrompt;
        public Map<String,Object> extras = new HashMap<>();
    }
}
