package bhavana.agenticsdlc.platform.workflow.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "context_artifact")
public class ContextArtifactEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID workflowId;
    @Column(nullable = false) private int workflowRevision;
    @Column(nullable = false, length = 100) private String artifactKey;
    @Column(nullable = false) private long artifactVersion;
    @Column(nullable = false, length = 100) private String producerTaskId;
    @Column(nullable = false, length = 30) private String schemaVersion;
    @Column(nullable = false, length = 64) private String contentHash;
    @Column(nullable = false, columnDefinition = "text") private String inputHashesJson;
    @Column(nullable = false, columnDefinition = "text") private String contentJson;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected ContextArtifactEntity() {
    }
}

