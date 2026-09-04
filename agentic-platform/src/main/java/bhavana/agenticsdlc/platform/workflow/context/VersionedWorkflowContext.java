package bhavana.agenticsdlc.platform.workflow.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VersionedWorkflowContext {
    private final Map<String, List<ContextArtifact>> artifactsByKey = new HashMap<>();

    public synchronized void append(ContextArtifact artifact) {
        List<ContextArtifact> versions = artifactsByKey.computeIfAbsent(
                artifact.artifactVersion().key(), ignored -> new ArrayList<>());
        long expectedVersion = versions.size() + 1L;
        if (artifact.artifactVersion().version() != expectedVersion) {
            throw new IllegalArgumentException("Expected version %d for artifact %s but received %d".formatted(
                    expectedVersion, artifact.artifactVersion().key(), artifact.artifactVersion().version()));
        }
        versions.add(artifact);
    }

    public synchronized Optional<ContextArtifact> latest(String key) {
        return artifactsByKey.getOrDefault(key, List.of()).stream()
                .max(Comparator.comparing(ContextArtifact::artifactVersion));
    }

    public synchronized Optional<ContextArtifact> get(ArtifactVersion version) {
        return artifactsByKey.getOrDefault(version.key(), List.of()).stream()
                .filter(artifact -> artifact.artifactVersion().equals(version))
                .findFirst();
    }

    public synchronized List<ContextArtifact> history(String key) {
        return List.copyOf(artifactsByKey.getOrDefault(key, List.of()));
    }
}
