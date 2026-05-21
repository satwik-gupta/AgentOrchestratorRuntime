package com.agentorchestrator.bootstrap;

import com.agentorchestrator.core.ChatMessage;
import com.agentorchestrator.network.AgentStreamPublisher;
import com.agentorchestrator.tools.ToolDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal, vanilla-Java client that supports streaming OpenAI-style chat completions and
 * can call registered tools via ToolDispatcher when the model emits a function_call.
 */
public final class OllamaOrOpenAiClient implements AutoCloseable {
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String baseUrl;
    private final String apiKey;
    private final boolean openAiMode;
    private final ExecutorService executor;

    public OllamaOrOpenAiClient(String baseUrl, String apiKey, boolean openAiMode) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.apiKey = apiKey; // optional for local ollama
        this.openAiMode = openAiMode;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .executor(executor)
                .build();
    }

    /**
     * Stream a chat completion as tokens into an AgentStreamPublisher. Function-calls emitted by the
     * model are forwarded to the provided ToolDispatcher for execution.
     */
    public AgentStreamPublisher streamChatCompletion(List<ChatMessage> history,
                                                     List<ObjectNode> functionSchemas,
                                                     ToolDispatcher toolDispatcher,
                                                     String model) throws IOException {
        AgentStreamPublisher publisher = new AgentStreamPublisher();

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model == null ? "gpt-4o-mini" : model);
        ArrayNode msgs = payload.putArray("messages");
        for (ChatMessage m : history) {
            ObjectNode jm = mapper.createObjectNode();
            jm.put("role", mapRole(m.getRole()));
            jm.put("content", m.getContent());
            msgs.add(jm);
        }
        // request streaming
        payload.put("stream", true);
        if (functionSchemas != null && !functionSchemas.isEmpty()) payload.set("functions", mapper.valueToTree(functionSchemas));
        payload.put("function_call", "auto");

        String endpoint = openAiMode ? baseUrl + "/v1/chat/completions" : baseUrl + "/api/generate";
        HttpRequest.Builder reqb = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMinutes(5))
                .POST(BodyPublishers.ofString(mapper.writeValueAsString(payload)));
        reqb.header("Content-Type", "application/json");
        if (openAiMode && apiKey != null && !apiKey.isBlank()) reqb.header("Authorization", "Bearer " + apiKey);

        HttpRequest req = reqb.build();

        CompletableFuture<HttpResponse<InputStream>> cf = client.sendAsync(req, BodyHandlers.ofInputStream());
        cf.whenCompleteAsync((resp, ex) -> {
            if (ex != null) {
                publisher.publish("[LLM-ERROR] " + ex.getMessage());
                publisher.complete();
                return;
            }
            try (InputStream in = resp.body();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                StringBuilder functionArgs = new StringBuilder();
                String functionName = null;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    // OpenAI streams prefix data: . Ollama may send raw JSON lines.
                    String payloadLine = line;
                    if (payloadLine.startsWith("data: ")) payloadLine = payloadLine.substring(6).trim();
                    if ("[DONE]".equals(payloadLine)) break;
                    if (!payloadLine.startsWith("{")) continue; // ignore non-json lines

                    JsonNode node = mapper.readTree(payloadLine);
                    // OpenAI style: choices[].delta.content OR choices[].delta.function_call
                    JsonNode choices = node.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode ch = choices.get(0);
                        JsonNode delta = ch.path("delta");
                        if (delta.has("content")) {
                            String content = delta.get("content").asText("");
                            if (!content.isEmpty()) publisher.publish(content);
                        }
                        if (delta.has("function_call")) {
                            JsonNode fc = delta.get("function_call");
                            if (fc.has("name")) functionName = fc.get("name").asText();
                            if (fc.has("arguments")) {
                                String part = fc.get("arguments").asText("");
                                functionArgs.append(part);
                            }
                        }

                        // If model indicates function_call finished, invoke tool
                        JsonNode finishReason = ch.path("finish_reason");
                        if (!finishReason.isMissingNode() && "function_call".equals(finishReason.asText(null))) {
                            if (functionName != null && functionArgs.length() > 0 && toolDispatcher != null) {
                                try {
                                    Map<String, Object> argsMap = mapper.readValue(functionArgs.toString(), Map.class);
                                    ObjectNode toolResult = toolDispatcher.dispatch(functionName, argsMap);
                                    // publish the tool result as a JSON snippet in the stream
                                    publisher.publish(mapper.writeValueAsString(toolResult));
                                } catch (Exception tEx) {
                                    publisher.publish("[TOOL-ERROR] " + tEx.getMessage());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                try { publisher.publish("[LLM-ERROR] " + e.getMessage()); } catch (Exception ignore) {}
            } finally {
                publisher.complete();
            }
        }, executor);

        return publisher;
    }

    private String mapRole(com.agentorchestrator.core.ChatRole r) {
        switch (r) {
            case USER: return "user";
            case SYSTEM: return "system";
            case AGENT: return "assistant";
            case SUMMARY: return "system";
            default: return "user";
        }
    }

    @Override
    public void close() throws Exception {
        try {
            executor.shutdownNow();
        } catch (Exception ignored) { }
    }
}
