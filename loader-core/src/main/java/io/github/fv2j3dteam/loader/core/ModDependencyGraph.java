package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModDependency;
import io.github.fv2j3dteam.api.ModDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class ModDependencyGraph {
    private final Map<String, ModDescriptor> descriptors;

    public ModDependencyGraph(Iterable<? extends ModContainer> containers) {
        Map<String, ModDescriptor> descriptorMap = new HashMap<>();
        for (ModContainer container : containers) {
            if (container == null) {
                continue;
            }
            descriptorMap.put(container.id(), container.descriptor());
        }
        this.descriptors = Map.copyOf(descriptorMap);
    }

    public List<String> resolveOrder() {
        if (descriptors.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        Set<String> ids = new HashSet<>(descriptors.keySet());

        for (String id : ids) {
            ModDescriptor descriptor = descriptors.get(id);
            Set<String> dependencies = new HashSet<>();
            for (ModDependency dependency : descriptor.dependencies()) {
                if (descriptors.containsKey(dependency.id())) {
                    dependencies.add(dependency.id());
                }
            }
            for (ModDependency dependency : descriptor.optionalDependencies()) {
                if (descriptors.containsKey(dependency.id())) {
                    dependencies.add(dependency.id());
                }
            }
            indegree.put(id, dependencies.size());
            for (String dependencyId : dependencies) {
                dependents.computeIfAbsent(dependencyId, ignored -> new ArrayList<>()).add(id);
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.naturalOrder());
        for (String id : ids) {
            if (indegree.getOrDefault(id, 0) == 0) {
                ready.add(id);
            }
        }

        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.poll();
            ordered.add(current);
            for (String dependent : dependents.getOrDefault(current, List.of())) {
                int remaining = indegree.getOrDefault(dependent, 0) - 1;
                indegree.put(dependent, remaining);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != descriptors.size()) {
            List<String> cyclePath = detectCycle();
            throw new IllegalStateException(
                    "Dependency cycle detected: " + (cyclePath.isEmpty() ? String.join(", ", new ArrayList<>(descriptors.keySet())) : String.join(" -> ", cyclePath))
            );
        }

        return List.copyOf(ordered);
    }

    private List<String> detectCycle() {
        List<String> orderedIds = new ArrayList<>(new HashSet<>(descriptors.keySet()));
        orderedIds.sort(Comparator.naturalOrder());
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<String> stack = new ArrayList<>();
        for (String id : orderedIds) {
            List<String> cycle = visit(id, descriptors, visiting, visited, stack);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private List<String> visit(
            String id,
            Map<String, ModDescriptor> descriptorMap,
            Set<String> visiting,
            Set<String> visited,
            List<String> stack
    ) {
        if (visiting.contains(id)) {
            int startIndex = stack.indexOf(id);
            if (startIndex >= 0) {
                return new ArrayList<>(stack.subList(startIndex, stack.size()));
            }
            return List.of(id);
        }
        if (visited.contains(id)) {
            return List.of();
        }

        visiting.add(id);
        stack.add(id);
        ModDescriptor descriptor = descriptorMap.get(id);
        if (descriptor != null) {
            for (ModDependency dependency : descriptor.dependencies()) {
                if (descriptorMap.containsKey(dependency.id())) {
                    List<String> cycle = visit(dependency.id(), descriptorMap, visiting, visited, stack);
                    if (!cycle.isEmpty()) {
                        return cycle;
                    }
                }
            }
            for (ModDependency dependency : descriptor.optionalDependencies()) {
                if (descriptorMap.containsKey(dependency.id())) {
                    List<String> cycle = visit(dependency.id(), descriptorMap, visiting, visited, stack);
                    if (!cycle.isEmpty()) {
                        return cycle;
                    }
                }
            }
        }

        stack.remove(stack.size() - 1);
        visiting.remove(id);
        visited.add(id);
        return List.of();
    }
}
