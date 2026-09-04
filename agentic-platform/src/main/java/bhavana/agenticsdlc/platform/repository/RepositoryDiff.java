package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Produces bounded, reviewer-readable full-file unified hunks for changed Java sources. */
public final class RepositoryDiff {
    public String between(Path baseline, Path current, int maximumBytes) {
        var before = new ManifestService().capture(baseline);
        var after = new ManifestService().capture(current);
        Set<String> names = new TreeSet<>(before.files().keySet());
        names.addAll(after.files().keySet());
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (!name.startsWith("src/") || !name.endsWith(".java")
                    || Objects.equals(before.files().get(name), after.files().get(name))) continue;
            String old = read(baseline.resolve(name), maximumBytes);
            String updated = read(current.resolve(name), maximumBytes);
            var left = old.lines().toList();
            var right = updated.lines().toList();
            result.append("--- ").append(before.files().containsKey(name) ? "a/" + name : "/dev/null").append('\n');
            result.append("+++ ").append(after.files().containsKey(name) ? "b/" + name : "/dev/null").append('\n');
            result.append("@@ -").append(left.isEmpty() ? 0 : 1).append(',').append(left.size())
                    .append(" +").append(right.isEmpty() ? 0 : 1).append(',').append(right.size()).append(" @@\n");
            left.forEach(line -> result.append('-').append(line).append('\n'));
            if (!old.isEmpty() && !old.endsWith("\n")) result.append("\\ No newline at end of file\n");
            right.forEach(line -> result.append('+').append(line).append('\n'));
            if (!updated.isEmpty() && !updated.endsWith("\n")) result.append("\\ No newline at end of file\n");
            if (result.toString().getBytes(StandardCharsets.UTF_8).length > maximumBytes)
                throw new RepositorySecurityException("Diff exceeds bound");
        }
        return result.toString();
    }

    private String read(Path path, int bound) {
        if (!Files.exists(path)) return "";
        try {
            if (Files.size(path) > bound || Files.isSymbolicLink(path))
                throw new RepositorySecurityException("Unsafe diff input");
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot produce diff", e);
        }
    }
}
