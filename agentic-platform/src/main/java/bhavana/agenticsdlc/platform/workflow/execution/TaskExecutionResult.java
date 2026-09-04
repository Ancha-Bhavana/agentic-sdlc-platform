package bhavana.agenticsdlc.platform.workflow.execution;

import java.util.Objects;

public record TaskExecutionResult(boolean successful, boolean retryable, String summary) {
    public TaskExecutionResult {
        summary = Objects.requireNonNull(summary, "summary");
        if (successful && retryable) {
            throw new IllegalArgumentException("A successful result cannot be retryable");
        }
    }

    public static TaskExecutionResult success(String summary) {
        return new TaskExecutionResult(true, false, summary);
    }

    public static TaskExecutionResult failure(String summary, boolean retryable) {
        return new TaskExecutionResult(false, retryable, summary);
    }
}

