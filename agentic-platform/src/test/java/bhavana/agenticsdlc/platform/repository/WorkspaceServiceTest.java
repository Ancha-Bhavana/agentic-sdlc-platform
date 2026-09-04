package bhavana.agenticsdlc.platform.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceServiceTest {
    @TempDir Path temp;

    @Test void createsIsolatedRevisionWorkspaceAndImmutableBaselineEvidence() throws Exception {
        Path approved = Files.createDirectory(temp.resolve("approved"));
        Path repository = Files.createDirectory(approved.resolve("source"));
        Files.writeString(repository.resolve("pom.xml"), "baseline");
        WorkspaceService service = new WorkspaceService(temp.resolve("runtime"), approved);
        UUID workflowId = UUID.randomUUID();

        WorkspaceHandle first = service.create(workflowId, 1, repository);
        Files.writeString(first.path().resolve("pom.xml"), "changed");
        WorkspaceHandle second = service.create(workflowId, 2, repository);

        assertThat(first.path()).isNotEqualTo(second.path());
        assertThat(Files.readString(second.path().resolve("pom.xml"))).isEqualTo("baseline");
        assertThat(first.baseline().rootHash()).isEqualTo(second.baseline().rootHash());
        assertThat(new ManifestService().capture(first.path()).rootHash()).isNotEqualTo(first.baseline().rootHash());
        assertThat(service.rollback(first).rootHash()).isEqualTo(first.baseline().rootHash());
        assertThat(Files.readString(first.path().resolve("pom.xml"))).isEqualTo("baseline");
    }
}
