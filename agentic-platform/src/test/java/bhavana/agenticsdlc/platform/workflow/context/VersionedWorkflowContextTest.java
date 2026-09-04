package bhavana.agenticsdlc.platform.workflow.context;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedWorkflowContextTest {
    @Test
    void retainsHistoryAndReturnsLatestVersionWithLineage() {
        VersionedWorkflowContext context = new VersionedWorkflowContext();
        context.append(artifact(1, 1, Map.of("requirement", "hash-1")));
        context.append(artifact(2, 2, Map.of("requirement", "hash-2")));

        assertThat(context.history("design")).hasSize(2);
        assertThat(context.latest("design")).get()
                .extracting(value -> value.artifactVersion().version(), ContextArtifact::workflowRevision)
                .containsExactly(2L, 2);
        assertThat(context.get(new ArtifactVersion("design", 1))).get()
                .extracting(ContextArtifact::inputHashes)
                .isEqualTo(Map.of("requirement", "hash-1"));
    }

    @Test
    void rejectsVersionGaps() {
        VersionedWorkflowContext context = new VersionedWorkflowContext();
        assertThatThrownBy(() -> context.append(artifact(2, 1, Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected version 1");
    }

    private ContextArtifact artifact(long version, int workflowRevision, Map<String, String> inputs) {
        return new ContextArtifact(new ArtifactVersion("design", version), workflowRevision,
                "architecture", "1.0", "content-hash-" + version, inputs,
                "{\"decision\":\"version-%d\"}".formatted(version), Instant.parse("2026-01-01T00:00:00Z"));
    }
}

