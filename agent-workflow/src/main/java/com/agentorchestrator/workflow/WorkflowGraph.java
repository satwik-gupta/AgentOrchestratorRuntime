package com.agentorchestrator.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkflowGraph {
    private final Map<String, WorkflowTask> tasks = new ConcurrentHashMap<>();

    public void addTask(WorkflowTask task) {
        Objects.requireNonNull(task, "task");
        if (tasks.putIfAbsent(task.getId(), task) != null) {
            throw new IllegalArgumentException("Task with id already exists: " + task.getId());
        }
    }

    public WorkflowTask getTask(String id) {
        return tasks.get(id);
    }

    public Collection<WorkflowTask> getTasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    public void validateNoCycles() {
        // ensure all dependencies reference known tasks
        for (WorkflowTask t : tasks.values()) {
            for (String dep : t.getDependencies()) {
                if (!tasks.containsKey(dep)) {
                    throw new IllegalStateException("Task '" + t.getId() + "' depends on unknown task '" + dep + "'");
                }
            }
        }

        // detect cycles using Kahn's algorithm
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (String id : tasks.keySet()) {
            inDegree.put(id, 0);
            adjacency.put(id, new HashSet<>());
        }
        for (WorkflowTask t : tasks.values()) {
            for (String dep : t.getDependencies()) {
                adjacency.get(dep).add(t.getId());
                inDegree.put(t.getId(), inDegree.get(t.getId()) + 1);
            }
        }

        Deque<String> q = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) q.add(e.getKey());
        }

        int visited = 0;
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            visited++;
            for (String neigh : adjacency.get(id)) {
                inDegree.put(neigh, inDegree.get(neigh) - 1);
                if (inDegree.get(neigh) == 0) q.add(neigh);
            }
        }
        if (visited != tasks.size()) {
            throw new IllegalStateException("Cycle detected in workflow graph");
        }
    }

    public Map<String, Set<String>> buildDependentsMap() {
        Map<String, Set<String>> dependents = new HashMap<>();
        for (String id : tasks.keySet()) dependents.put(id, new HashSet<>());
        for (WorkflowTask t : tasks.values()) {
            for (String dep : t.getDependencies()) {
                dependents.get(dep).add(t.getId());
            }
        }
        return dependents;
    }

    public List<WorkflowTask> topologicalSort() {
        validateNoCycles();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (String id : tasks.keySet()) {
            inDegree.put(id, 0);
            adjacency.put(id, new HashSet<>());
        }
        for (WorkflowTask t : tasks.values()) {
            for (String dep : t.getDependencies()) {
                adjacency.get(dep).add(t.getId());
                inDegree.put(t.getId(), inDegree.get(t.getId()) + 1);
            }
        }
        Deque<String> q = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) if (e.getValue() == 0) q.add(e.getKey());
        List<WorkflowTask> ordered = new ArrayList<>();
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            ordered.add(tasks.get(id));
            for (String neigh : adjacency.get(id)) {
                inDegree.put(neigh, inDegree.get(neigh) - 1);
                if (inDegree.get(neigh) == 0) q.add(neigh);
            }
        }
        return ordered;
    }
}
