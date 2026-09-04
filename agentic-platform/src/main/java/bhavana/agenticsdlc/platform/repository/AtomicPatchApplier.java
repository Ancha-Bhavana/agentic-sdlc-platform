package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

public final class AtomicPatchApplier {
    private final FileHashService hashes = new FileHashService();

    public RepositoryManifest apply(Path workspace, List<FileOperation> operations, PatchPolicy policy) {
        policy.validate(workspace, operations);
        Path staging = workspace.resolveSibling(workspace.getFileName() + ".staging-" + System.nanoTime());
        try {
            copyTree(workspace, staging);
            SafePathResolver resolver = new SafePathResolver(staging.getParent());
            for (FileOperation operation : operations) {
                Path target = resolver.resolve(staging, operation.path());
                if (operation.type() == FileOperation.Type.DELETE) Files.delete(target);
                else {
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Path temporary = Files.createTempFile(target.getParent(), ".apply-", ".tmp");
                    Files.write(temporary, operation.content());
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
            Path backup = workspace.resolveSibling(workspace.getFileName() + ".backup-" + System.nanoTime());
            Files.move(workspace, backup, StandardCopyOption.ATOMIC_MOVE);
            try { Files.move(staging, workspace, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception failure) { Files.move(backup, workspace, StandardCopyOption.ATOMIC_MOVE); throw failure; }
            deleteTree(backup);
            return new ManifestService().capture(workspace);
        } catch (Exception failure) {
            deleteTreeQuietly(staging);
            throw new IllegalStateException("Atomic patch application failed", failure);
        }
    }

    public void verifyRollback(Path workspace, RepositoryManifest baseline) {
        if (!hashes.sha256(baseline.rootHash().getBytes()).equals(hashes.sha256(new ManifestService().capture(workspace).rootHash().getBytes())))
            throw new IllegalStateException("Rollback manifest does not match baseline");
    }

    private void copyTree(Path source, Path destination) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path, target);
            }
        }
    }
    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    private void deleteTreeQuietly(Path root) { try { deleteTree(root); } catch (IOException ignored) { } }
}
