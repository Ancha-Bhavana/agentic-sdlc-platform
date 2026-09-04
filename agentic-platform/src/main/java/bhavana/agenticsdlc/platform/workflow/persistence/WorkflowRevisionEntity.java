package bhavana.agenticsdlc.platform.workflow.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_revision")
@IdClass(WorkflowRevisionEntity.Key.class)
public class WorkflowRevisionEntity {
    @Id private UUID workflowId;
    @Id private int revision;
    @Column(nullable = false, length = 64) private String requirementHash;
    @Column(nullable = false, length = 64) private String repositoryHash;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected WorkflowRevisionEntity() {
    }

    public WorkflowRevisionEntity(UUID workflowId, int revision, String requirementHash,
                                  String repositoryHash, Instant createdAt) {
        if (workflowId == null || revision < 1 || requirementHash == null || !requirementHash.matches("[0-9a-f]{64}")
                || repositoryHash == null || !repositoryHash.matches("[0-9a-f]{64}") || createdAt == null) {
            throw new IllegalArgumentException("Valid workflow revision hashes and timestamp are required");
        }
        this.workflowId = workflowId;
        this.revision = revision;
        this.requirementHash = requirementHash;
        this.repositoryHash = repositoryHash;
        this.createdAt = createdAt;
    }

    public UUID getWorkflowId() { return workflowId; }
    public int getRevision() { return revision; }
    public String getRequirementHash() { return requirementHash; }
    public String getRepositoryHash() { return repositoryHash; }
    public Instant getCreatedAt() { return createdAt; }

    public record Key(UUID workflowId, int revision) implements Serializable {}
}
