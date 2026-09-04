package bhavana.agenticsdlc.platform.workflow;

import bhavana.agenticsdlc.platform.agent.*;
import bhavana.agenticsdlc.platform.repository.*;
import bhavana.agenticsdlc.platform.validation.*;
import bhavana.agenticsdlc.platform.workflow.context.*;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import bhavana.agenticsdlc.platform.workflow.execution.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Executes one governed workflow revision; the caller supplies authenticated gate decisions. */
public final class LifecycleExecution implements TaskRunner {
    private final WorkspaceHandle workspace;
    private final WorkspaceService workspaces;
    private final String requirement;
    private final AgentCatalog agents;
    private final BuildRunner builds;
    private final ObjectMapper mapper;
    private final int maximumRepairs;
    private final Duration buildTimeout;
    private final VersionedWorkflowContext artifacts = new VersionedWorkflowContext();
    private final Map<String, AgentResult> results = new ConcurrentHashMap<>();
    private final RepositoryContextSelector selector = new RepositoryContextSelector(30, 48_000);
    private final AtomicPatchApplier patches = new AtomicPatchApplier();
    private final List<ValidationResult> validations = new ArrayList<>();
    private List<FileOperation> proposal = List.of();

    public LifecycleExecution(WorkspaceHandle workspace, WorkspaceService workspaces, String requirement,
                              AgentCatalog agents, BuildRunner builds, ObjectMapper mapper,
                              int maximumRepairs, Duration buildTimeout) {
        if (requirement == null || requirement.isBlank() || maximumRepairs < 0 || maximumRepairs > 10
                || buildTimeout == null || buildTimeout.isNegative() || buildTimeout.isZero())
            throw new IllegalArgumentException("Valid requirement, repair bound and build timeout required");
        this.workspace = workspace;
        this.workspaces = workspaces;
        this.requirement = requirement;
        this.agents = agents;
        this.builds = builds;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.maximumRepairs = maximumRepairs;
        this.buildTimeout = buildTimeout;
    }

    public WorkflowExecutionReport run(GateEvaluator gates, Duration timeout, CancellationToken token) {
        var report = new WorkflowScheduler(attempt -> Duration.ZERO).execute(
                new LifecycleGraphFactory().create(), this, gates, timeout, token);
        if (Set.of(WorkflowStatus.FAILED, WorkflowStatus.CANCELLED, WorkflowStatus.REJECTED)
                .contains(report.status())) {
            var restored = workspaces.rollback(workspace);
            save("rollback", "rollback", json(restored), Map.of());
        }
        return report;
    }

    public VersionedWorkflowContext artifacts() { return artifacts; }

    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        context.cancellationToken().throwIfCancelled();
        String id = context.task().id();
        try {
            switch (id) {
                case "patch-policy" -> {
                    var decoder = new PatchProposalDecoder(mapper);
                    var combined = new ArrayList<>(decoder.decode(results.get("implementation")));
                    combined.addAll(decoder.decode(results.get("tests")));
                    if (combined.isEmpty()) throw new RepositorySecurityException("Engineering proposal is empty");
                    proposal = List.copyOf(combined);
                    validateProposal();
                    save(id, id, json(proposal), inputs(context));
                }
                case "apply" -> apply(context);
                case "validate" -> validate(context);
                case "repair" -> {
                    for (int attempt = 0; !latestValidation().successful() && attempt < maximumRepairs; attempt++) {
                        context.cancellationToken().throwIfCancelled();
                        AgentResult repair = invoke("repair", AgentRole.REPAIR, context, true);
                        proposal = new PatchProposalDecoder(mapper).decode(repair);
                        if (proposal.isEmpty()) throw new RepositorySecurityException("Repair proposal is empty");
                        validateProposal();
                        apply(context);
                        validate(context);
                    }
                    if (!latestValidation().successful())
                        return TaskExecutionResult.failure("Validation failed; repair budget exhausted", false);
                    save("repair", "repair", "Validation succeeded", inputs(context));
                }
                default -> invoke(id, roleFor(id), context,
                        !Set.of("understand", "ambiguity").contains(id));
            }
            return TaskExecutionResult.success(id + " completed");
        } catch (RuntimeException failure) {
            return TaskExecutionResult.failure(id + " failed: " + failure.getClass().getSimpleName(), false);
        }
    }

    private AgentRole roleFor(String id) {
        return switch (id) {
            case "understand" -> AgentRole.REQUIREMENT_UNDERSTANDING;
            case "ambiguity" -> AgentRole.AMBIGUITY_CLARIFICATION;
            case "repository" -> AgentRole.REPOSITORY_ANALYSIS;
            case "decompose" -> AgentRole.TASK_DECOMPOSITION;
            case "design" -> AgentRole.ARCHITECTURE_DESIGN;
            case "implementation" -> AgentRole.IMPLEMENTATION;
            case "tests" -> AgentRole.TEST_GENERATION;
            case "documentation" -> AgentRole.DOCUMENTATION;
            case "risk" -> AgentRole.SECURITY_RISK_REVIEW;
            case "release" -> AgentRole.RELEASE_READINESS;
            default -> throw new IllegalArgumentException("Unsupported lifecycle task");
        };
    }

    private void validateProposal() {
        for (FileOperation operation : proposal) {
            String path = operation.path().replace('\\', '/');
            if (!(path.startsWith("src/") && path.endsWith(".java")))
                throw new RepositorySecurityException("Only Java source/test changes are admitted in this checkpoint");
        }
        new PatchPolicy(workspace.path(), 64_000).validate(workspace.path(), proposal);
    }

    private void apply(TaskExecutionContext context) {
        context.cancellationToken().throwIfCancelled();
        var manifest = patches.apply(workspace.path(), proposal, new PatchPolicy(workspace.path(), 64_000));
        save("patch", context.task().id(), json(proposal), inputs(context));
        save("manifest", context.task().id(), json(manifest), inputs(context));
        save("diff", context.task().id(),
                new RepositoryDiff().between(workspace.baselinePath(), workspace.path(), 128_000), inputs(context));
    }

    private void validate(TaskExecutionContext context) {
        ValidationResult result = builds.run(workspace.path(), BuildCapability.MAVEN_VERIFY,
                buildTimeout, context.cancellationToken());
        validations.add(result);
        save("validation-evidence", context.task().id(), json(result), inputs(context));
        invoke("validation-analysis", AgentRole.VALIDATION, context, true);
    }

    private ValidationResult latestValidation() { return validations.getLast(); }

    private AgentResult invoke(String key, AgentRole role, TaskExecutionContext context, boolean repository) {
        Map<String, String> upstream = new TreeMap<>();
        inputs(context).keySet().forEach(input ->
                artifacts.latest(input).ifPresent(artifact -> upstream.put(input, artifact.content())));
        if (Set.of(AgentRole.REPAIR, AgentRole.VALIDATION, AgentRole.RELEASE_READINESS,
                AgentRole.DOCUMENTATION, AgentRole.SECURITY_RISK_REVIEW).contains(role)) {
            for (String input : List.of("patch", "diff", "validation-evidence", "design"))
                artifacts.latest(input).ifPresent(artifact -> upstream.put(input, artifact.content()));
        }
        AgentResult result = agents.require(role).execute(new AgentRequest(
                workspace.workflowId().toString(), workspace.revision(), requirement, upstream,
                repository ? selector.select(workspace.path(), Set.of()) : Map.of()));
        results.put(key, result);
        Map<String, String> hashes = new TreeMap<>();
        upstream.forEach((input, value) -> hashes.put(input, hash(value)));
        save(key, context.task().id(), json(result), hashes);
        return result;
    }

    private Map<String, String> inputs(TaskExecutionContext context) {
        Map<String, String> hashes = new TreeMap<>();
        context.task().dependencies().forEach(input ->
                artifacts.latest(input).ifPresent(artifact -> hashes.put(input, artifact.contentHash())));
        return hashes;
    }

    private synchronized void save(String key, String producer, String content, Map<String, String> hashes) {
        long version = artifacts.history(key).size() + 1L;
        artifacts.append(new ContextArtifact(new ArtifactVersion(key, version), workspace.revision(),
                producer, "1.0", hash(content), hashes, content, Instant.now()));
    }

    private String hash(String value) {
        return new FileHashService().sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode lifecycle evidence", e); }
    }
}
