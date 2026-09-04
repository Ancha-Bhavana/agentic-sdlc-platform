package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class RepositoryContextSelector {
    private static final Set<String> EXCLUDED = Set.of(
            ".git", ".m2", ".mvn", "target", "build", "node_modules", ".idea");
    private final int maximumFiles;
    private final int maximumBytes;

    public RepositoryContextSelector(int maximumFiles, int maximumBytes) {
        if (maximumFiles < 1 || maximumBytes < 1)
            throw new IllegalArgumentException("Context bounds must be positive");
        this.maximumFiles = maximumFiles;
        this.maximumBytes = maximumBytes;
    }

    public Map<String, String> select(Path root, Set<String> terms) {
        Map<String, String> result = new LinkedHashMap<>();
        int[] used = {0};
        try {
            if (Files.isSymbolicLink(root)) throw new RepositorySecurityException("Symbolic context root");
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return EXCLUDED.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    if (result.size() >= maximumFiles) return FileVisitResult.TERMINATE;
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!attrs.isRegularFile()
                            || !(name.endsWith(".java") || name.endsWith(".md") || name.equals("pom.xml"))
                            || attrs.size() > maximumBytes - used[0]) return FileVisitResult.CONTINUE;
                    byte[] bytes;
                    try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                        bytes = stream.readNBytes(maximumBytes - used[0] + 1);
                    }
                    if (bytes.length > maximumBytes - used[0]) return FileVisitResult.CONTINUE;
                    for (byte value : bytes) if (value == 0) return FileVisitResult.CONTINUE;
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    if (content.matches("(?s).*(?i:password|secret|api[_-]?key|BEGIN .*PRIVATE KEY).*"))
                        return FileVisitResult.CONTINUE;
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    boolean relevant = name.equals("pom.xml") || terms.isEmpty() || terms.stream().anyMatch(term ->
                            (relative + content).toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)));
                    if (relevant) {
                        result.put(relative, content);
                        used[0] += bytes.length;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot assemble repository context", e);
        }
        return Map.copyOf(result);
    }
}
