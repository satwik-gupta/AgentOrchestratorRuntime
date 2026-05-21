package com.agentorchestrator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ToolRegistry {
    private final ConcurrentMap<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public void register(Object instance) {
        Objects.requireNonNull(instance, "instance");
        Method[] methods = instance.getClass().getMethods();
        for (Method m : methods) {
            Tool ann = m.getAnnotation(Tool.class);
            if (ann != null) {
                String name = ann.name();
                ToolEntry entry = new ToolEntry(name, ann.description(), instance, m);
                if (tools.putIfAbsent(name, entry) != null) {
                    throw new IllegalStateException("Tool already registered with name: " + name);
                }
            }
        }
    }

    public ToolEntry getTool(String name) {
        return tools.get(name);
    }

    public Collection<ToolEntry> getAll() {
        return tools.values();
    }

    public ArrayNode listToolSchemas() {
        ArrayNode arr = mapper.createArrayNode();
        ToolMetadataParser parser = new ToolMetadataParser(mapper);
        for (ToolEntry e : tools.values()) {
            ObjectNode schema = parser.parse(e);
            arr.add(schema);
        }
        return arr;
    }

    public static final class ToolEntry {
        private final String name;
        private final String description;
        private final Object instance;
        private final Method method;

        public ToolEntry(String name, String description, Object instance, Method method) {
            this.name = name;
            this.description = description;
            this.instance = instance;
            this.method = method;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Object getInstance() {
            return instance;
        }

        public Method getMethod() {
            return method;
        }
    }
}
