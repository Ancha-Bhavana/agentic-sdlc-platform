package bhavana.agenticsdlc.platform.workflow.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public record TaskDefinition(
        String id,
        TaskType type,
        Set<String> dependencies,
        GateType entryGate,
        GateType exitGate,
        int maximumAttempts,
        Duration timeout,
        String fallbackTaskId) {

    public TaskDefinition(String id, TaskType type, Set<String> dependencies,
                          GateType entryGate, GateType exitGate,
                          int maximumAttempts, Duration timeout) {
        this(id, type, dependencies, entryGate, exitGate, maximumAttempts, timeout, null);
    }

    public TaskDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Task id must not be blank");
        }
        type = Objects.requireNonNull(type, "type");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        entryGate = Objects.requireNonNull(entryGate, "entryGate");
        exitGate = Objects.requireNonNull(exitGate, "exitGate");
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Task cannot depend on itself: " + id);
        }
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("Maximum attempts must be at least one");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Task timeout must be positive");
        }
        if (id.equals(fallbackTaskId)) {
            throw new IllegalArgumentException("Task cannot be its own fallback: " + id);
        }
    }
}
