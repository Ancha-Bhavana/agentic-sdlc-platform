package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowRecoveryPlanner {
    public Map<String, TaskStatus> recover(List<TaskCheckpoint> checkpoints, Instant now) {
        Map<String, TaskStatus> recovered = new LinkedHashMap<>();
        for (TaskCheckpoint checkpoint : checkpoints) {
            TaskStatus status = checkpoint.status();
            if (status == TaskStatus.RUNNING && checkpoint.leaseExpiresAt() != null
                    && !checkpoint.leaseExpiresAt().isAfter(now)) {
                status = TaskStatus.READY;
            }
            recovered.put(checkpoint.taskId(), status);
        }
        return Map.copyOf(recovered);
    }
}
