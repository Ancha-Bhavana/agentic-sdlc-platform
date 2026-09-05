package bhavana.agenticsdlc.platform.scenario;

import bhavana.agenticsdlc.platform.config.CoordinationProperties;
import bhavana.agenticsdlc.platform.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Applies requirement-specific source and test changes in an isolated revision workspace. */
@Service
public class GeneratedScenarioMutationService {
    private final WorkspaceService workspaces;
    private final Path runtimeRoot;

    public GeneratedScenarioMutationService(CoordinationProperties properties,
            @Value("${agentic-sdlc.repository.approved-root:.}") Path approvedRepositoryRoot) {
        runtimeRoot = Path.of(properties.runtimeRoot()).toAbsolutePath().normalize();
        workspaces = new WorkspaceService(runtimeRoot, approvedRepositoryRoot);
    }

    public MutationEvidence mutate(UUID workflowId, int revision, ScenarioType type, Path repository,
                                   String requirement, boolean deliberatelyBroken) {
        WorkspaceHandle workspace = workspace(workflowId, revision, repository);
        String module = Files.isDirectory(workspace.path().resolve("url-shortener-service"))
                ? "url-shortener-service/" : "";
        String suffix = workflowId.toString().replace("-", "").substring(0, 12) + "R" + revision;
        String className = "GeneratedUrlPolicy" + suffix;
        String sourcePath = module + "src/main/java/bhavana/agenticsdlc/shortener/generated/" + className + ".java";
        String testPath = module + "src/test/java/bhavana/agenticsdlc/shortener/generated/" + className + "Test.java";
        String digest = new FileHashService().sha256(requirement.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        String source = "package bhavana.agenticsdlc.shortener.generated;\n\n"
                + "/** Generated for " + type.name().toLowerCase(Locale.ROOT) + " requirement " + digest + ". */\n"
                + "public final class " + className + " {\n"
                + "  private " + className + "() {}\n"
                + "  public static String normalizeAlias(String alias) {\n"
                + (deliberatelyBroken ? "    return alias.trim().toLowerCase(java.util.Locale.ROOT)\n"
                : "    if (alias == null || alias.isBlank()) throw new IllegalArgumentException(\"Alias is required\");\n"
                + "    String normalized = alias.trim().toLowerCase(java.util.Locale.ROOT);\n"
                + "    if (!normalized.matches(\"[a-z0-9-]{3,32}\")) throw new IllegalArgumentException(\"Unsafe alias\");\n"
                + "    return normalized;\n")
                + "  }\n}\n";
        String test = "package bhavana.agenticsdlc.shortener.generated;\n\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import static org.assertj.core.api.Assertions.*;\n\n"
                + "class " + className + "Test {\n"
                + "  @Test void normalizesAndRejectsUnsafeAliases() {\n"
                + "    assertThat(" + className + ".normalizeAlias(\" My-Link \" )).isEqualTo(\"my-link\");\n"
                + "    assertThatThrownBy(() -> " + className + ".normalizeAlias(\"../admin\"))\n"
                + "        .isInstanceOf(IllegalArgumentException.class);\n"
                + "  }\n}\n";
        apply(workspace, sourcePath, source);
        apply(workspace, testPath, test);
        return evidence(workspace, List.of(sourcePath, testPath), deliberatelyBroken);
    }

    public MutationEvidence repair(UUID workflowId, int revision, ScenarioType type, Path repository,
                                   String requirement) {
        return mutate(workflowId, revision, type, repository, requirement, false);
    }

    public void rollback(UUID workflowId, int revision) {
        Path revisionRoot = runtimeRoot.resolve(workflowId.toString()).resolve("revision-" + revision);
        Path repository = revisionRoot.resolve("repository"), baseline = revisionRoot.resolve("baseline");
        if (!Files.isDirectory(repository) || !Files.isDirectory(baseline)) return;
        workspaces.rollback(new WorkspaceHandle(workflowId, revision, repository, baseline,
                new ManifestService().capture(baseline)));
    }

    private WorkspaceHandle workspace(UUID workflowId, int revision, Path repository) {
        Path revisionRoot = runtimeRoot.resolve(workflowId.toString()).resolve("revision-" + revision);
        if (!Files.isDirectory(revisionRoot.resolve("repository"))) return workspaces.create(workflowId, revision, repository);
        Path baseline = revisionRoot.resolve("baseline");
        return new WorkspaceHandle(workflowId, revision, revisionRoot.resolve("repository"), baseline,
                new ManifestService().capture(baseline));
    }

    private void apply(WorkspaceHandle workspace, String relative, String content) {
        Path target = workspace.path().resolve(relative);
        FileOperation.Type type = Files.exists(target) ? FileOperation.Type.UPDATE : FileOperation.Type.CREATE;
        String expected = null;
        if (type == FileOperation.Type.UPDATE) {
            try { expected = new FileHashService().sha256(Files.readAllBytes(target)); }
            catch (java.io.IOException failure) { throw new IllegalStateException("Cannot hash generated source", failure); }
        }
        new AtomicPatchApplier().apply(workspace.path(), List.of(new FileOperation(type, relative, expected,
                content.getBytes(StandardCharsets.UTF_8))), new PatchPolicy(workspace.path(), 64_000));
    }

    private MutationEvidence evidence(WorkspaceHandle workspace, List<String> paths, boolean broken) {
        RepositoryManifest manifest = new ManifestService().capture(workspace.path());
        return new MutationEvidence(paths, manifest.rootHash(),
                new RepositoryDiff().between(workspace.baselinePath(), workspace.path(), 128_000),
                workspace.path(), broken);
    }

    public record MutationEvidence(List<String> paths, String manifestHash, String diff,
                                   Path workspacePath, boolean deliberatelyBroken) {
        public MutationEvidence { paths = List.copyOf(paths); }
    }
}
