package com.agentorchestrator.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentStateMachine {
    private final AtomicReference<AgentState> state = new AtomicReference<>(AgentState.IDLE);

    private static final Map<AgentState, Set<AgentState>> ALLOWED;

    static {
        Map<AgentState, Set<AgentState>> m = new EnumMap<>(AgentState.class);
        m.put(AgentState.IDLE, Set.of(AgentState.PLANNING));
        m.put(AgentState.PLANNING, Set.of(AgentState.EXECUTING_TOOL, AgentState.COMPLETED, AgentState.ERROR));
        m.put(AgentState.EXECUTING_TOOL, Set.of(AgentState.AWAITING_RESPONSE, AgentState.COMPLETED, AgentState.ERROR));
        m.put(AgentState.AWAITING_RESPONSE, Set.of(AgentState.EXECUTING_TOOL, AgentState.COMPLETED, AgentState.ERROR));
        m.put(AgentState.COMPLETED, Set.of(AgentState.IDLE));
        m.put(AgentState.ERROR, Set.of(AgentState.IDLE));
        ALLOWED = Collections.unmodifiableMap(m);
    }

    public AgentState getState() {
        return state.get();
    }

    public void transitionTo(AgentState next) throws StateTransitionException {
        Objects.requireNonNull(next, "next");
        for (;;) {
            AgentState current = state.get();
            Set<AgentState> allowed = ALLOWED.get(current);
            if (allowed == null || !allowed.contains(next)) {
                throw new StateTransitionException("Invalid transition from " + current + " to " + next);
            }
            if (state.compareAndSet(current, next)) {
                return;
            }
            // CAS failed due to concurrent change; retry
        }
    }

    public boolean tryTransition(AgentState next) {
        Objects.requireNonNull(next, "next");
        for (;;) {
            AgentState current = state.get();
            Set<AgentState> allowed = ALLOWED.get(current);
            if (allowed == null || !allowed.contains(next)) {
                return false;
            }
            if (state.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public void forceSet(AgentState forced) {
        Objects.requireNonNull(forced, "forced");
        state.set(forced);
    }
}
