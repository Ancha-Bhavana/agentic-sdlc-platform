package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;

import java.time.Instant;

public record TaskCheckpoint(String taskId, TaskStatus status, Instant leaseExpiresAt) {
}

