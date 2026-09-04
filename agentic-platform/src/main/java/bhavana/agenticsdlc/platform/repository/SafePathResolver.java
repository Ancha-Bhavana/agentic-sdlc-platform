package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class SafePathResolver {
    private final Path root;

    public SafePathResolver(Path root) {
        try { this.root = root.toRealPath(LinkOption.NOFOLLOW_LINKS); }
        catch (IOException e) { throw new RepositorySecurityException("Approved root is unavailable"); }
    }

    public Path admitRepository(Path candidate) {
        try {
            Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(root) || real.equals(root)) throw new RepositorySecurityException("Repository is outside approved root");
            rejectSymbolicLinks(real);
            return real;
        } catch (IOException e) { throw new RepositorySecurityException("Repository cannot be resolved"); }
    }

    public Path resolve(Path workspace, String relative) {
        if (relative == null || relative.isBlank()) throw new RepositorySecurityException("Path is blank");
        Path supplied = Path.of(relative);
        if (supplied.isAbsolute()) throw new RepositorySecurityException("Absolute paths are forbidden");
        Path normalized = workspace.resolve(supplied).normalize();
        if (!normalized.startsWith(workspace.normalize())) throw new RepositorySecurityException("Path traversal is forbidden");
        rejectExistingParentLinks(workspace.normalize(), normalized);
        return normalized;
    }

    private void rejectExistingParentLinks(Path workspace, Path target) {
        Path cursor = workspace;
        for (Path part : workspace.relativize(target)) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor))
                throw new RepositorySecurityException("Symbolic links are forbidden in workspace paths");
        }
    }

    private void rejectSymbolicLinks(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            if (stream.anyMatch(Files::isSymbolicLink)) throw new RepositorySecurityException("Repository contains a symbolic link");
        }
    }
}
