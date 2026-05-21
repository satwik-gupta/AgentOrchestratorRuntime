package com.agentorchestrator.network;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class AgentNetworkClient {
    private final String host;
    private final int port;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public AgentNetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String callTool(String toolName, Map<String, Object> args) throws IOException, InterruptedException {
        URI uri = URI.create("http://" + host + ":" + port + "/tools/call");
        Map<String, Object> body = Map.of("name", toolName, "args", args == null ? Map.of() : args);
        String json = mapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
        return resp.body();
    }

    public String executeWorkflow(String toolName, Map<String, Object> args) throws IOException, InterruptedException {
        URI uri = URI.create("http://" + host + ":" + port + "/workflow/execute");
        Map<String, Object> body = Map.of("toolName", toolName, "args", args == null ? Map.of() : args);
        String json = mapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json").POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
        return resp.body();
    }
}
