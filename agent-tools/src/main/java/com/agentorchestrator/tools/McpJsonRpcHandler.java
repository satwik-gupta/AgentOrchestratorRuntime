package com.agentorchestrator.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class McpJsonRpcHandler {
    private final ToolRegistry registry;
    private final ToolDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public McpJsonRpcHandler(ToolRegistry registry, ToolDispatcher dispatcher) {
        this.registry = registry;
        this.dispatcher = dispatcher;
    }

    public String handleSingle(String json) {
        try {
            JsonNode req = mapper.readTree(json);
            String jsonrpc = req.path("jsonrpc").asText(null);
            JsonNode idNode = req.get("id");
            Object id = (idNode == null || idNode.isNull()) ? null : (idNode.isNumber() ? idNode.numberValue() : idNode.asText());
            String method = req.path("method").asText(null);
            JsonNode params = req.get("params");
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            if (id != null) resp.set("id", idNode);

            if (!"2.0".equals(jsonrpc)) {
                ObjectNode err = mapper.createObjectNode();
                err.put("code", -32600);
                err.put("message", "Invalid jsonrpc version");
                resp.set("error", err);
                return mapper.writeValueAsString(resp);
            }

            if ("tools/list".equals(method)) {
                resp.set("result", registry.listToolSchemas());
                return mapper.writeValueAsString(resp);
            }

            if ("tools/call".equals(method)) {
                if (params == null || !params.has("name")) {
                    ObjectNode err = mapper.createObjectNode();
                    err.put("code", -32602);
                    err.put("message", "Missing params.name");
                    resp.set("error", err);
                    return mapper.writeValueAsString(resp);
                }
                String name = params.path("name").asText();
                JsonNode argsNode = params.path("args");
                Map<String, Object> args;
                if (argsNode == null || argsNode.isMissingNode() || argsNode.isNull()) {
                    args = new HashMap<>();
                } else {
                    args = mapper.convertValue(argsNode, new TypeReference<Map<String, Object>>() {});
                }
                ObjectNode result = dispatcher.dispatch(name, args);
                resp.set("result", result);
                return mapper.writeValueAsString(resp);
            }

            ObjectNode err = mapper.createObjectNode();
            err.put("code", -32601);
            err.put("message", "Method not found: " + method);
            resp.set("error", err);
            return mapper.writeValueAsString(resp);

        } catch (JsonProcessingException e) {
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            ObjectNode err = mapper.createObjectNode();
            err.put("code", -32700);
            err.put("message", "Parse error: " + e.getMessage());
            resp.set("error", err);
            try { return mapper.writeValueAsString(resp); } catch (JsonProcessingException ex) { return "{" + "\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"parse error\"}}"; }
        } catch (IOException e) {
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            ObjectNode err = mapper.createObjectNode();
            err.put("code", -32603);
            err.put("message", "Internal error: " + e.getMessage());
            resp.set("error", err);
            try { return mapper.writeValueAsString(resp); } catch (JsonProcessingException ex) { return "{" + "\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"internal error\"}}"; }
        }
    }

    public void handleStdIo(InputStream in, OutputStream out) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             PrintWriter pw = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String response = handleSingle(line);
                pw.println(response);
                pw.flush();
            }
        }
    }

    public void sendSseEvent(OutputStream out, String id, Object payload) throws IOException {
        String json = mapper.writeValueAsString(payload);
        StringBuilder sb = new StringBuilder();
        if (id != null) sb.append("id: ").append(id).append('\n');
        for (String line : json.split("\n")) {
            sb.append("data: ").append(line).append('\n');
        }
        sb.append('\n');
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
