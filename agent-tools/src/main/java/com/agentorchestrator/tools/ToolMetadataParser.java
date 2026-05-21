package com.agentorchestrator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public final class ToolMetadataParser {
    private final ObjectMapper mapper;

    public ToolMetadataParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode parse(ToolRegistry.ToolEntry entry) {
        Method method = entry.getMethod();
        ObjectNode root = mapper.createObjectNode();
        root.put("name", entry.getName());
        root.put("description", entry.getDescription() == null ? "" : entry.getDescription());

        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();

        Parameter[] params = method.getParameters();
        for (Parameter p : params) {
            ToolParam ann = p.getAnnotation(ToolParam.class);
            if (ann == null) {
                throw new IllegalStateException("All tool method parameters must be annotated with @ToolParam: " + method);
            }
            String pname = ann.name();
            ObjectNode pnode = mapper.createObjectNode();
            String jtype = mapJavaToJsonType(p.getType());
            pnode.put("type", jtype);
            if (!ann.description().isEmpty()) pnode.put("description", ann.description());
            properties.set(pname, pnode);
            if (ann.required()) required.add(pname);
        }

        parameters.set("properties", properties);
        if (required.size() > 0) parameters.set("required", required);
        root.set("parameters", parameters);
        return root;
    }

    private String mapJavaToJsonType(Class<?> cls) {
        if (cls.isArray()) return "array";
        if (Number.class.isAssignableFrom(cls) || cls.isPrimitive() && (cls == int.class || cls == long.class || cls == short.class || cls == byte.class)) return "integer";
        if (cls == double.class || cls == float.class || Double.class.isAssignableFrom(cls) || Float.class.isAssignableFrom(cls)) return "number";
        if (Boolean.class.isAssignableFrom(cls) || cls == boolean.class) return "boolean";
        if (CharSequence.class.isAssignableFrom(cls) || cls == char.class) return "string";
        if (java.util.Collection.class.isAssignableFrom(cls)) return "array";
        if (java.util.Map.class.isAssignableFrom(cls)) return "object";
        return "object";
    }
}
