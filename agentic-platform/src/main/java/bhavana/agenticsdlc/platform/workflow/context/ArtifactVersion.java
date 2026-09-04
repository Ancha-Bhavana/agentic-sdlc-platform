package bhavana.agenticsdlc.platform.workflow.context;

public record ArtifactVersion(String key, long version) implements Comparable<ArtifactVersion> {
    public ArtifactVersion {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Artifact key must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Artifact version must be positive");
        }
    }

    @Override
    public int compareTo(ArtifactVersion other) {
        int keyOrder = key.compareTo(other.key);
        return keyOrder == 0 ? Long.compare(version, other.version) : keyOrder;
    }
}

