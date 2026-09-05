package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.config.CoordinationProperties;
import bhavana.agenticsdlc.platform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Applies a deterministic, reviewable source mutation in an isolated revision workspace. */
@Service
public class GeneratedScenarioMutationService {
    private final WorkspaceService workspaces;
    private final Path runtimeRoot;

    public GeneratedScenarioMutationService(CoordinationProperties properties,
            @Value("${agentic-sdlc.repository.approved-root:.}") Path approvedRepositoryRoot) {
        runtimeRoot = Path.of(properties.runtimeRoot()).toAbsolutePath().normalize();
        workspaces = new WorkspaceService(runtimeRoot, approvedRepositoryRoot);
    }

    public MutationEvidence mutate(UUID workflowId, int revision, ScenarioType type, Path repository) {
        Path revisionRoot = runtimeRoot.resolve(workflowId.toString()).resolve("revision-" + revision);
        WorkspaceHandle workspace;
        if (Files.isDirectory(revisionRoot.resolve("repository"))) {
            Path baseline = revisionRoot.resolve("baseline");
            workspace = new WorkspaceHandle(workflowId, revision, revisionRoot.resolve("repository"), baseline,
                    new ManifestService().capture(baseline));
        } else workspace = workspaces.create(workflowId, revision, repository);

        String className = "Workflow" + workflowId.toString().replace("-", "") + "Revision" + revision;
        String relative = "src/test/java/bhavana/agenticsdlc/generated/" + className + ".java";
        String content = "package bhavana.agenticsdlc.generated;\n\n"
                + "/** Deterministic " + type.name().toLowerCase(Locale.ROOT) + " workflow mutation. */\n"
                + "final class " + className + " { static final int REVISION = " + revision + "; }\n";
        Path target = workspace.path().resolve(relative);
        if (!Files.exists(target)) {
            new AtomicPatchApplier().apply(workspace.path(), List.of(new FileOperation(FileOperation.Type.CREATE,
                    relative, null, content.getBytes(StandardCharsets.UTF_8))), new PatchPolicy(workspace.path(), 64_000));
        } else {
            try {
                if (!Files.readString(target).equals(content))
                    throw new RepositorySecurityException("Existing deterministic mutation does not match claimed execution");
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot verify deterministic mutation", failure);
            }
        }
        RepositoryManifest manifest = new ManifestService().capture(workspace.path());
        return new MutationEvidence(relative, manifest.rootHash(),
                new RepositoryDiff().between(workspace.baselinePath(), workspace.path(), 128_000));
    }

    public record MutationEvidence(String path, String manifestHash, String diff) {}
}
