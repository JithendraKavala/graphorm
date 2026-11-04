package com.graphorm.schema.validation;

import com.graphorm.schema.ast.BlockAttributeNode;
import com.graphorm.schema.ast.ExpressionNode;
import com.graphorm.schema.ast.FieldNode;
import com.graphorm.schema.ast.IdentifierNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.ViewModelNode;

import java.util.*;

/**
 * Detects circular dependencies among view models in a schema document.
 */
public final class CycleDetector {

    private CycleDetector() {}

    /**
     * Checks for cycles in view dependencies.
     *
     * @param document schema document
     * @return a list of view names representing a cycle (e.g. [A, B, A]), or empty if acyclic
     */
    public static Optional<List<String>> findCycle(SchemaDocument document) {
        if (document == null || document.viewModels().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (ViewModelNode vm : document.viewModels()) {
            Set<String> deps = new LinkedHashSet<>();

            // 1. Dependency from @@from(SourceModel)
            Optional<BlockAttributeNode> fromAttr = vm.fromAttribute();
            if (fromAttr.isPresent()) {
                extractModelDependency(fromAttr.get(), document).ifPresent(deps::add);
            }

            // 2. Dependencies from @@leftJoin and @@innerJoin
            for (BlockAttributeNode join : vm.joinAttributes()) {
                extractModelDependency(join, document).ifPresent(deps::add);
            }

            // 3. Dependencies from fields whose type is another view
            for (FieldNode field : vm.fields()) {
                String typeName = field.type().name();
                if (document.containsViewModel(typeName)) {
                    deps.add(typeName);
                }
            }

            graph.put(vm.name(), deps);
        }

        // DFS for cycle detection
        Set<String> visited = new HashSet<>();
        Set<String> onStack = new HashSet<>();
        List<String> path = new ArrayList<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                if (dfs(node, graph, visited, onStack, path)) {
                    // Extract exact cycle sublist
                    String start = path.get(path.size() - 1);
                    int startIdx = path.indexOf(start);
                    return Optional.of(new ArrayList<>(path.subList(startIdx, path.size())));
                }
            }
        }

        return Optional.empty();
    }

    private static boolean dfs(
            String current,
            Map<String, Set<String>> graph,
            Set<String> visited,
            Set<String> onStack,
            List<String> path
    ) {
        visited.add(current);
        onStack.add(current);
        path.add(current);

        Set<String> neighbors = graph.getOrDefault(current, Set.of());
        for (String next : neighbors) {
            if (!visited.contains(next)) {
                if (dfs(next, graph, visited, onStack, path)) {
                    return true;
                }
            } else if (onStack.contains(next)) {
                path.add(next);
                return true;
            }
        }

        onStack.remove(current);
        path.remove(path.size() - 1);
        return false;
    }

    private static Optional<String> extractModelDependency(BlockAttributeNode attr, SchemaDocument doc) {
        // First positional argument or named argument 'model'
        Optional<ExpressionNode> argVal = attr.getPositionalArgumentValue(0);
        if (argVal.isEmpty()) {
            argVal = attr.getArgumentValue("model");
        }
        if (argVal.isPresent() && argVal.get() instanceof IdentifierNode idNode) {
            String target = idNode.name();
            if (doc.containsViewModel(target)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
