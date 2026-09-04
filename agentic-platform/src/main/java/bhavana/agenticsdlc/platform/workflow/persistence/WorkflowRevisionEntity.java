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

    public record Key(UUID workflowId, int revision) implements Serializable {}
}

