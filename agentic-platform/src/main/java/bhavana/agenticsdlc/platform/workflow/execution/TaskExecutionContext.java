package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;

import java.util.Objects;

public record TaskExecutionContext(
        TaskDefinition task,
        int attempt,
        CancellationToken cancellationToken) {

    public TaskExecutionContext {
        task = Objects.requireNonNull(task, "task");
        cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (attempt < 1) {
            throw new IllegalArgumentException("Attempt must be positive");
        }
    }
}

