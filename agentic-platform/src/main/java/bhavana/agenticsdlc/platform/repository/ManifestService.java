package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class ManifestService {
    private final FileHashService hashes = new FileHashService();

    public RepositoryManifest capture(Path root) {
        var entries = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(p -> !Files.isSymbolicLink(p)).sorted()
                    .forEach(p -> entries.put(root.relativize(p).toString().replace('\\', '/'), hashes.sha256(p)));
        } catch (IOException e) { throw new IllegalStateException("Cannot capture repository manifest", e); }
        String canonical = entries.entrySet().stream().map(e -> e.getKey() + "\0" + e.getValue() + "\n")
                .collect(java.util.stream.Collectors.joining());
        return new RepositoryManifest(entries, hashes.sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
