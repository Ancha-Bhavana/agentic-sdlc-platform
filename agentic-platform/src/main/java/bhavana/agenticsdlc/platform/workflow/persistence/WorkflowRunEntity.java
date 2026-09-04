package bhavana.agenticsdlc.platform.workflow.persistence;

import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_run")
public class WorkflowRunEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, updatable = false, length = 80)
    private String correlationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowStatus status;
    @Column(nullable = false)
    private int currentRevision;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long entityVersion;

    protected WorkflowRunEntity() {
    }

    public WorkflowRunEntity(UUID id, String correlationId, Instant now) {
        this.id = id;
        this.correlationId = correlationId;
        this.status = WorkflowStatus.SUBMITTED;
        this.currentRevision = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transitionTo(WorkflowStatus target, Instant now) {
        status.requireTransitionTo(target);
        status = target;
        updatedAt = now;
    }

    public void beginRevision(int revision, Instant now) {
        if (revision != currentRevision + 1) {
            throw new IllegalArgumentException("Workflow revision must increase by one");
        }
        if (status != WorkflowStatus.RUNNING && status != WorkflowStatus.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("Workflow cannot be replanned from " + status);
        }
        currentRevision = revision;
        status = WorkflowStatus.RUNNING;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public WorkflowStatus getStatus() { return status; }
    public int getCurrentRevision() { return currentRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getEntityVersion() { return entityVersion; }
}
