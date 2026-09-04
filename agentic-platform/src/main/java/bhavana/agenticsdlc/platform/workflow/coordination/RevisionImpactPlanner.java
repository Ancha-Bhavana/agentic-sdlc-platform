package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;
import java.util.*;

public final class RevisionImpactPlanner {
    public RevisionImpactPlan plan(WorkflowGraph graph, Map<String, TaskStatus> previous,
                                   Set<String> changedTaskIds) {
        if (changedTaskIds.isEmpty() || !graph.tasks().keySet().containsAll(changedTaskIds)) {
            throw new IllegalArgumentException("At least one known changed task is required");
        }
        Set<String> affected = new HashSet<>(changedTaskIds);
        boolean changed;
        do {
            changed = false;
            for (var task : graph.tasks().values()) {
                if (!affected.contains(task.id()) && task.dependencies().stream().anyMatch(affected::contains)) {
                    changed |= affected.add(task.id());
                }
            }
        } while (changed);

        Set<String> reused = new HashSet<>();
        Map<String, TaskStatus> next = new LinkedHashMap<>();
        for (String taskId : graph.tasks().keySet()) {
            if (affected.contains(taskId)) {
                next.put(taskId, TaskStatus.PENDING);
            } else if (previous.getOrDefault(taskId, TaskStatus.PENDING).satisfiesDependency()) {
                reused.add(taskId);
                next.put(taskId, TaskStatus.REUSED);
            } else {
                next.put(taskId, TaskStatus.PENDING);
            }
        }
        return new RevisionImpactPlan(affected, reused, next);
    }
}
