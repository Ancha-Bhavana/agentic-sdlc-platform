package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Comparator;
import java.util.Set;

public final class WorkspaceService {
    private final Path runtimeRoot;
    private final SafePathResolver repositoryAdmission;

    public WorkspaceService(Path runtimeRoot, Path approvedRepositoryRoot) {
        this.runtimeRoot = runtimeRoot.toAbsolutePath().normalize();
        this.repositoryAdmission = new SafePathResolver(approvedRepositoryRoot);
    }

    public WorkspaceHandle create(UUID workflowId, int revision, Path repository) {
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Path source = repositoryAdmission.admitRepository(repository);
        Path revisionRoot = runtimeRoot.resolve(workflowId.toString()).resolve("revision-" + revision);
        Path baseline = revisionRoot.resolve("baseline");
        Path workspace = revisionRoot.resolve("repository");
        if (Files.exists(workspace)) throw new IllegalStateException("Workspace already exists");
        copyTree(source, baseline);
        copyTree(baseline, workspace);
        return new WorkspaceHandle(workflowId, revision, workspace, baseline, new ManifestService().capture(baseline));
    }

    public RepositoryManifest rollback(WorkspaceHandle handle) {
        deleteTree(handle.path());
        copyTree(handle.baselinePath(), handle.path());
        RepositoryManifest restored = new ManifestService().capture(handle.path());
        if (!restored.rootHash().equals(handle.baseline().rootHash()))
            throw new IllegalStateException("Rollback verification failed");
        return restored;
    }

    private void copyTree(Path source, Path destination) {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.filter(path -> !excluded(source, path)).toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path, target);
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot copy workspace tree", e); }
    }

    private boolean excluded(Path source, Path candidate) {
        if (candidate.equals(source)) return false;
        for (Path segment : source.relativize(candidate)) {
            if (Set.of(".git", ".idea", ".env", "target", "runtime-data").contains(segment.toString())) return true;
        }
        return false;
    }

    private void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException e) { throw new IllegalStateException("Cannot reset workspace", e); }
    }
}
