package bhavana.agenticsdlc.platform.workflow.persistence;

import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import bhavana.agenticsdlc.platform.workflow.domain.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.io.Serializable;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;

@Entity
@Table(name = "workflow_task")
@IdClass(WorkflowTaskEntity.Key.class)
public class WorkflowTaskEntity {
    @Id private UUID workflowId;
    @Id private int workflowRevision;
    @Id @Column(length = 100) private String taskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private TaskType taskType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TaskStatus status;
    @Column(nullable = false) private int attempt;
    private Instant leaseExpiresAt;
    private Instant startedAt;
    private Instant finishedAt;
    @Version private long entityVersion;

    protected WorkflowTaskEntity() {
    }

    public WorkflowTaskEntity(UUID workflowId, int workflowRevision, TaskDefinition definition,
                              TaskStatus status) {
        if (workflowId == null || workflowRevision < 1 || definition == null || status == null) {
            throw new IllegalArgumentException("Valid workflow task identity and status are required");
        }
        this.workflowId = workflowId;
        this.workflowRevision = workflowRevision;
        this.taskId = definition.id();
        this.taskType = definition.type();
        this.status = status;
    }

    public void invalidate(Instant now) {
        if (status != TaskStatus.SUCCEEDED && status != TaskStatus.REUSED) {
            throw new IllegalStateException("Only reusable task output can be invalidated");
        }
        status = TaskStatus.INVALIDATED;
        finishedAt = now;
        leaseExpiresAt = null;
    }

    public void cancel(Instant now) {
        if (!status.isFinished()) {
            status = TaskStatus.CANCELLED;
            finishedAt = now;
            leaseExpiresAt = null;
        }
    }

    public void recoverIfLeaseExpired(Instant now) {
        if (status == TaskStatus.RUNNING && leaseExpiresAt != null && !leaseExpiresAt.isAfter(now)) {
            status = TaskStatus.READY;
            leaseExpiresAt = null;
        }
    }

    public void start(Instant now, Duration leaseDuration) {
        if (status != TaskStatus.PENDING && status != TaskStatus.READY) {
            throw new IllegalStateException("Task cannot start from " + status);
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Task lease must be positive");
        }
        status = TaskStatus.RUNNING;
        attempt++;
        startedAt = now;
        finishedAt = null;
        leaseExpiresAt = now.plus(leaseDuration);
    }

    public void succeed(Instant now) {
        finish(TaskStatus.SUCCEEDED, now);
    }

    public void fail(Instant now) {
        finish(TaskStatus.FAILED, now);
    }

    public void reuse(Instant now) {
        if (status != TaskStatus.PENDING && status != TaskStatus.READY) throw new IllegalStateException("Task cannot be reused from " + status);
        status = TaskStatus.REUSED; finishedAt = now; leaseExpiresAt = null;
    }

    private void finish(TaskStatus target, Instant now) {
        if (status != TaskStatus.RUNNING) throw new IllegalStateException("Task is not running");
        status = target;
        finishedAt = now;
        leaseExpiresAt = null;
    }

    public UUID getWorkflowId() { return workflowId; }
    public int getWorkflowRevision() { return workflowRevision; }
    public String getTaskId() { return taskId; }
    public TaskType getTaskType() { return taskType; }
    public TaskStatus getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public record Key(UUID workflowId, int workflowRevision, String taskId) implements Serializable {}
}
