package com.agentorchestrator.tools;

import com.agentorchestrator.core.AgentState;
import com.agentorchestrator.core.AgentStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ToolDispatcher {
    private final ToolRegistry registry;
    private final AgentStateMachine agentStateMachine;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public ToolDispatcher(ToolRegistry registry, AgentStateMachine agentStateMachine) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.agentStateMachine = Objects.requireNonNull(agentStateMachine, "agentStateMachine");
    }

    public ObjectNode dispatch(String toolName, Map<String, Object> rawArgs) {
        ToolRegistry.ToolEntry entry = registry.getTool(toolName);
        if (entry == null) {
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "Unknown tool: " + toolName);
            return err;
        }

        Method method = entry.getMethod();
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        List<String> missing = paramsToNames(params).stream()
                .filter(n -> !rawArgs.containsKey(n))
                .collect(Collectors.toList());
        // we will check required by annotation below

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            ToolParam t = p.getAnnotation(ToolParam.class);
            if (t == null) {
                throw new IllegalStateException("All parameters must have @ToolParam on method: " + method);
            }
            String pname = t.name();
            if (!rawArgs.containsKey(pname)) {
                if (t.required()) {
                    ObjectNode err = mapper.createObjectNode();
                    err.put("error", "Missing required parameter: " + pname);
                    return err;
                } else {
                    args[i] = null;
                    continue;
                }
            }
            Object rv = rawArgs.get(pname);
            args[i] = coerce(rv, p.getType());
        }

        try {
            agentStateMachine.transitionTo(AgentState.EXECUTING_TOOL);
        } catch (Exception ignore) {
            // best-effort: continue
        }

        try {
            Object result = method.invoke(entry.getInstance(), args);
            ObjectNode resNode = mapper.createObjectNode();
            resNode.set("result", mapper.valueToTree(result));
            try {
                agentStateMachine.transitionTo(AgentState.AWAITING_RESPONSE);
            } catch (Exception ignore) { }
            return resNode;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "Invocation error: " + cause.getMessage());
            err.put("type", cause.getClass().getName());
            err.put("time", Instant.now().toString());
            return err;
        } catch (Exception e) {
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "Dispatch error: " + e.getMessage());
            err.put("type", e.getClass().getName());
            err.put("time", Instant.now().toString());
            return err;
        }
    }

    private List<String> paramsToNames(Parameter[] params) {
        return java.util.Arrays.stream(params).map(p -> {
            ToolParam t = p.getAnnotation(ToolParam.class);
            return (t == null) ? p.getName() : t.name();
        }).collect(Collectors.toList());
    }

    private Object coerce(Object raw, Class<?> target) {
        if (raw == null) {
            if (target.isPrimitive()) {
                if (target == boolean.class) return false;
                if (target == byte.class) return (byte)0;
                if (target == short.class) return (short)0;
                if (target == int.class) return 0;
                if (target == long.class) return 0L;
                if (target == float.class) return 0f;
                if (target == double.class) return 0d;
                if (target == char.class) return '\0';
            }
            return null;
        }
        Class<?> boxed = box(target);
        if (boxed.isInstance(raw)) return raw;

        if (raw instanceof Number) {
            Number n = (Number) raw;
            if (boxed == Integer.class) return n.intValue();
            if (boxed == Long.class) return n.longValue();
            if (boxed == Double.class) return n.doubleValue();
            if (boxed == Float.class) return n.floatValue();
            if (boxed == Short.class) return n.shortValue();
            if (boxed == Byte.class) return n.byteValue();
        }

        if (raw instanceof String) {
            String s = (String) raw;
            if (boxed == String.class) return s;
            if (boxed == Integer.class) return Integer.parseInt(s);
            if (boxed == Long.class) return Long.parseLong(s);
            if (boxed == Double.class) return Double.parseDouble(s);
            if (boxed == Float.class) return Float.parseFloat(s);
            if (boxed == Boolean.class) return Boolean.parseBoolean(s);
            if (boxed == java.time.Instant.class) return java.time.Instant.parse(s);
        }

        if (raw instanceof Map && java.util.Map.class.isAssignableFrom(boxed)) return raw;
        if (raw instanceof List && java.util.Collection.class.isAssignableFrom(boxed)) return raw;

        throw new IllegalArgumentException("Cannot coerce value of type " + raw.getClass() + " to " + target);
    }

    private Class<?> box(Class<?> cls) {
        if (!cls.isPrimitive()) return cls;
        if (cls == int.class) return Integer.class;
        if (cls == long.class) return Long.class;
        if (cls == double.class) return Double.class;
        if (cls == float.class) return Float.class;
        if (cls == boolean.class) return Boolean.class;
        if (cls == byte.class) return Byte.class;
        if (cls == short.class) return Short.class;
        if (cls == char.class) return Character.class;
        return cls;
    }
}
