package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;

import java.time.Duration;
import java.util.Map;

public record WorkflowExecutionReport(
        WorkflowStatus status,
        Map<String, TaskStatus> taskStatuses,
        Map<String, Integer> attempts,
        Map<String, String> summaries,
        Duration duration) {

    public WorkflowExecutionReport {
        taskStatuses = Map.copyOf(taskStatuses);
        attempts = Map.copyOf(attempts);
        summaries = Map.copyOf(summaries);
    }
}

