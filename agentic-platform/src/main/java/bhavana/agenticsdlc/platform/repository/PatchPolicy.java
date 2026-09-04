package bhavana.agenticsdlc.platform.repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PatchPolicy {
    private static final Set<String> PROTECTED = Set.of(".git/config", ".env", "mvnw", "mvnw.cmd");
    private final SafePathResolver paths;
    private final FileHashService hashes = new FileHashService();
    private final long maximumBytes;

    public PatchPolicy(Path workspace, long maximumBytes) { this.paths = new SafePathResolver(workspace.getParent()); this.maximumBytes = maximumBytes; }

    public void validate(Path workspace, List<FileOperation> operations) {
        Set<String> seen = new HashSet<>(); long total = 0;
        for (FileOperation operation : operations) {
            String name = operation.path().replace('\\', '/');
            if (!seen.add(name)) throw new RepositorySecurityException("Duplicate operation: " + name);
            if (PROTECTED.contains(name) || name.startsWith(".git/")) throw new RepositorySecurityException("Protected file: " + name);
            Path target = paths.resolve(workspace, name);
            byte[] content = operation.content(); total += content == null ? 0 : content.length;
            if (total > maximumBytes) throw new RepositorySecurityException("Patch exceeds size limit");
            if (content != null && containsNullByte(content))
                throw new RepositorySecurityException("Binary content is forbidden");
            if (content != null && new String(content, StandardCharsets.UTF_8).contains("-----BEGIN PRIVATE KEY-----"))
                throw new RepositorySecurityException("Secret material detected");
            boolean exists = Files.isRegularFile(target);
            if (operation.type() == FileOperation.Type.CREATE && exists) throw new RepositorySecurityException("Create target exists: " + name);
            if (operation.type() != FileOperation.Type.CREATE) {
                if (!exists) throw new RepositorySecurityException("Target does not exist: " + name);
                if (operation.expectedHash() == null || !operation.expectedHash().equals(hashes.sha256(target)))
                    throw new RepositorySecurityException("Stale expected hash: " + name);
            }
        }
    }

    private boolean containsNullByte(byte[] content) {
        for (byte value : content) if (value == 0) return true;
        return false;
    }
}
