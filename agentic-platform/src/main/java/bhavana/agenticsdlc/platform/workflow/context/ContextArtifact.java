package bhavana.agenticsdlc.platform.workflow.context;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ContextArtifact(
        ArtifactVersion artifactVersion,
        int workflowRevision,
        String producerTaskId,
        String schemaVersion,
        String contentHash,
        Map<String, String> inputHashes,
        String content,
        Instant createdAt) {

    public ContextArtifact {
        artifactVersion = Objects.requireNonNull(artifactVersion, "artifactVersion");
        producerTaskId = requireText(producerTaskId, "producerTaskId");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        contentHash = requireText(contentHash, "contentHash");
        content = Objects.requireNonNull(content, "content");
        inputHashes = Map.copyOf(Objects.requireNonNull(inputHashes, "inputHashes"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (workflowRevision < 1) {
            throw new IllegalArgumentException("Workflow revision must be positive");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

