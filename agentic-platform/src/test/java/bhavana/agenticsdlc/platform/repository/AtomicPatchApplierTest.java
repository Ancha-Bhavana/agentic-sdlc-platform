package bhavana.agenticsdlc.platform.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicPatchApplierTest {
    @TempDir Path temp;

    @Test void appliesValidatedCreateUpdateAndDeleteAsOneProposal() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path update = workspace.resolve("update.txt"); Path delete = workspace.resolve("delete.txt");
        Files.writeString(update, "old"); Files.writeString(delete, "remove");
        FileHashService hashes = new FileHashService();
        List<FileOperation> operations = List.of(
                new FileOperation(FileOperation.Type.CREATE, "new.txt", null, "new".getBytes()),
                new FileOperation(FileOperation.Type.UPDATE, "update.txt", hashes.sha256(update), "changed".getBytes()),
                new FileOperation(FileOperation.Type.DELETE, "delete.txt", hashes.sha256(delete), null));

        new AtomicPatchApplier().apply(workspace, operations, new PatchPolicy(workspace, 100));

        assertThat(Files.readString(workspace.resolve("new.txt"))).isEqualTo("new");
        assertThat(Files.readString(workspace.resolve("update.txt"))).isEqualTo("changed");
        assertThat(workspace.resolve("delete.txt")).doesNotExist();
    }

    @Test void validationFailureLeavesWorkspaceByteForByteUnchanged() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Files.writeString(workspace.resolve("existing.txt"), "baseline");
        RepositoryManifest before = new ManifestService().capture(workspace);

        assertThatThrownBy(() -> new AtomicPatchApplier().apply(workspace, List.of(
                new FileOperation(FileOperation.Type.UPDATE, "existing.txt", "stale", "changed".getBytes())),
                new PatchPolicy(workspace, 100))).isInstanceOf(RepositorySecurityException.class);
        assertThat(new ManifestService().capture(workspace)).isEqualTo(before);
    }
}
