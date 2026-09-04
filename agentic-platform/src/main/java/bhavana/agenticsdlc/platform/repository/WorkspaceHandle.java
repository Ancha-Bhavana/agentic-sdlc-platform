package bhavana.agenticsdlc.platform.repository;

import java.nio.file.Path;
import java.util.UUID;

public record WorkspaceHandle(UUID workflowId, int revision, Path path, Path baselinePath, RepositoryManifest baseline) {}
