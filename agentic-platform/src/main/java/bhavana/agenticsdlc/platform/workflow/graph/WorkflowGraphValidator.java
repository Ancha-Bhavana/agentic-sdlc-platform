package bhavana.agenticsdlc.platform.workflow.graph;

import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowGraphValidator {
    private WorkflowGraphValidator() {
    }

    public static void validate(WorkflowGraph graph) {
        List<String> violations = new ArrayList<>();
        Set<String> ids = graph.tasks().keySet();
        graph.tasks().values().forEach(task -> task.dependencies().stream()
                .filter(dependency -> !ids.contains(dependency))
                .forEach(dependency -> violations.add(
                        "Task %s has missing dependency %s".formatted(task.id(), dependency))));
        graph.tasks().values().stream()
                .filter(task -> task.fallbackTaskId() != null)
                .filter(task -> !ids.contains(task.fallbackTaskId()))
                .forEach(task -> violations.add(
                        "Task %s has missing fallback %s".formatted(task.id(), task.fallbackTaskId())));
        if (violations.isEmpty()) {
            detectCycles(graph.tasks(), violations);
        }
        if (!violations.isEmpty()) {
            throw new InvalidWorkflowGraphException(violations);
        }
    }

    private static void detectCycles(Map<String, TaskDefinition> tasks, List<String> violations) {
        Map<String, VisitState> states = new HashMap<>();
        for (String id : tasks.keySet()) {
            visit(id, tasks, states, new HashSet<>(), violations);
            if (!violations.isEmpty()) {
                return;
            }
        }
    }

    private static void visit(String id, Map<String, TaskDefinition> tasks,
                              Map<String, VisitState> states, Set<String> path,
                              List<String> violations) {
        if (states.get(id) == VisitState.VISITED) {
            return;
        }
        if (states.get(id) == VisitState.VISITING) {
            path.add(id);
            violations.add("Cycle detected involving: " + path.stream().sorted().toList());
            return;
        }
        states.put(id, VisitState.VISITING);
        path.add(id);
        for (String dependency : tasks.get(id).dependencies()) {
            visit(dependency, tasks, states, new HashSet<>(path), violations);
            if (!violations.isEmpty()) {
                return;
            }
        }
        states.put(id, VisitState.VISITED);
    }

    private enum VisitState { VISITING, VISITED }
}
