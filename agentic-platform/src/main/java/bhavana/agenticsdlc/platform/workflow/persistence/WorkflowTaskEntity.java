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
import java.util.UUID;

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

    public record Key(UUID workflowId, int workflowRevision, String taskId) implements Serializable {}
}

