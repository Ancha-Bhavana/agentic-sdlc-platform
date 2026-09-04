package bhavana.agenticsdlc.platform.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositorySafetyTest {
    @TempDir Path temp;

    @Test void rejectsRepositoryOutsideApprovedRoot() throws Exception {
        Path approved = Files.createDirectory(temp.resolve("approved"));
        assertThatThrownBy(() -> new SafePathResolver(approved).admitRepository(temp))
                .isInstanceOf(RepositorySecurityException.class);
    }
    @Test void rejectsAbsoluteAndTraversalPaths() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        SafePathResolver resolver = new SafePathResolver(temp);
        assertThatThrownBy(() -> resolver.resolve(workspace, temp.resolve("escape").toString())).isInstanceOf(RepositorySecurityException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, "../escape")).isInstanceOf(RepositorySecurityException.class);
    }
    @Test void manifestChangesWhenContentChanges() throws Exception {
        Files.writeString(temp.resolve("a.txt"), "one");
        String before = new ManifestService().capture(temp).rootHash();
        Files.writeString(temp.resolve("a.txt"), "two");
        assertThat(new ManifestService().capture(temp).rootHash()).isNotEqualTo(before);
    }
    @Test void rejectsDuplicateProtectedOversizedSecretAndStaleOperations() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path existing = workspace.resolve("a.txt"); Files.writeString(existing, "current");
        PatchPolicy policy = new PatchPolicy(workspace, 10);
        assertThatThrownBy(() -> policy.validate(workspace, List.of(
                new FileOperation(FileOperation.Type.CREATE, "x.txt", null, "x".getBytes()),
                new FileOperation(FileOperation.Type.CREATE, "x.txt", null, "y".getBytes())))).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> policy.validate(workspace, List.of(new FileOperation(FileOperation.Type.CREATE, ".env", null, new byte[0])))).hasMessageContaining("Protected");
        assertThatThrownBy(() -> policy.validate(workspace, List.of(new FileOperation(FileOperation.Type.CREATE, "big.txt", null, new byte[11])))).hasMessageContaining("size");
        assertThatThrownBy(() -> new PatchPolicy(workspace, 100).validate(workspace, List.of(new FileOperation(FileOperation.Type.CREATE, "key.txt", null, "-----BEGIN PRIVATE KEY-----".getBytes())))).hasMessageContaining("Secret");
        assertThatThrownBy(() -> policy.validate(workspace, List.of(new FileOperation(FileOperation.Type.UPDATE, "a.txt", "wrong", "new".getBytes())))).hasMessageContaining("Stale");
    }
}
