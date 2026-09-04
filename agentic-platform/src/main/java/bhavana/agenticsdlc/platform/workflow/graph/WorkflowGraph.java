package bhavana.agenticsdlc.platform.workflow.graph;

import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WorkflowGraph {
    private final Map<String, TaskDefinition> tasks;

    public WorkflowGraph(Collection<TaskDefinition> definitions) {
        Map<String, TaskDefinition> indexed = new LinkedHashMap<>();
        for (TaskDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.id(), definition) != null) {
                throw new InvalidWorkflowGraphException(java.util.List.of("Duplicate task id: " + definition.id()));
            }
        }
        this.tasks = Map.copyOf(indexed);
        WorkflowGraphValidator.validate(this);
    }

    public Map<String, TaskDefinition> tasks() {
        return tasks;
    }

    public Set<String> readyTaskIds(Map<String, Boolean> dependencyCompletion) {
        return tasks.values().stream()
                .filter(task -> !Boolean.TRUE.equals(dependencyCompletion.get(task.id())))
                .filter(task -> task.dependencies().stream()
                        .allMatch(dependency -> Boolean.TRUE.equals(dependencyCompletion.get(dependency))))
                .map(TaskDefinition::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

