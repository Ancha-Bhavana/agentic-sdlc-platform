package bhavana.agenticsdlc.platform.repository;

import java.util.Map;

public record RepositoryManifest(Map<String, String> files, String rootHash) {
    public RepositoryManifest { files = Map.copyOf(files); }
}
