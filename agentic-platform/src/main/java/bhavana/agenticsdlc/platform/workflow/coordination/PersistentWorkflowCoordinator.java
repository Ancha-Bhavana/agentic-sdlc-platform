package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.repository.FileHashService;
import bhavana.agenticsdlc.platform.repository.RepositoryManifest;
import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

public class PersistentWorkflowCoordinator {
    private final WorkflowRunRepository runs;
    private final WorkflowRevisionRepository revisions;
    private final WorkflowTaskRepository tasks;
    private final WorkflowGraph graph;
    private final ActiveWorkflowRegistry active;
    private final Clock clock;
    private final FileHashService hashes = new FileHashService();
    private final RevisionImpactPlanner planner = new RevisionImpactPlanner();

    public PersistentWorkflowCoordinator(WorkflowRunRepository runs, WorkflowRevisionRepository revisions,
                                         WorkflowTaskRepository tasks, WorkflowGraph graph,
                                         ActiveWorkflowRegistry active, Clock clock) {
        this.runs = runs;
        this.revisions = revisions;
        this.tasks = tasks;
        this.graph = graph;
        this.active = active;
        this.clock = clock;
    }

    @Transactional
    public WorkflowRunEntity submit(UUID workflowId, String correlationId, String requirement,
                                    RepositoryManifest repository) {
        Instant now = clock.instant();
        WorkflowRunEntity run = new WorkflowRunEntity(workflowId, correlationId, now);
        runs.save(run);
        revisions.save(new WorkflowRevisionEntity(workflowId, 1, hash(requirement), repository.rootHash(), now));
        tasks.saveAll(graph.tasks().values().stream()
                .map(definition -> new WorkflowTaskEntity(workflowId, 1, definition, TaskStatus.PENDING)).toList());
        run.transitionTo(WorkflowStatus.RUNNING, now);
        return runs.save(run);
    }

    @Transactional
    public void pauseForClarification(UUID workflowId) {
        WorkflowRunEntity run = requireRun(workflowId);
        run.transitionTo(WorkflowStatus.AWAITING_CLARIFICATION, clock.instant());
        runs.save(run);
    }

    @Transactional
    public RevisionImpactPlan clarify(UUID workflowId, String clarifiedRequirement,
                                      RepositoryManifest repository) {
        WorkflowRunEntity run = requireRun(workflowId);
        if (run.getStatus() != WorkflowStatus.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("Workflow is not awaiting clarification");
        }
        return createRevision(run, clarifiedRequirement, repository, Set.of("ambiguity"));
    }

    @Transactional
    public RevisionImpactPlan replan(UUID workflowId, String revisedRequirement,
                                     RepositoryManifest repository, Set<String> changedTaskIds) {
        return createRevision(requireRun(workflowId), revisedRequirement, repository, changedTaskIds);
    }

    @Transactional
    public Map<String, TaskStatus> recover(UUID workflowId) {
        WorkflowRunEntity run = requireRun(workflowId);
        Instant now = clock.instant();
        List<WorkflowTaskEntity> current = currentTasks(run);
        current.forEach(task -> task.recoverIfLeaseExpired(now));
        tasks.saveAll(current);
        Map<String, TaskStatus> recovered = new LinkedHashMap<>();
        current.forEach(task -> recovered.put(task.getTaskId(), task.getStatus()));
        return Map.copyOf(recovered);
    }

    @Transactional
    public void taskStarted(UUID workflowId, int revision, String taskId, Duration leaseDuration) {
        WorkflowRunEntity run = requireCurrentRunningRevision(workflowId, revision);
        WorkflowTaskEntity task = requireTask(run.getId(), revision, taskId);
        task.start(clock.instant(), leaseDuration);
        tasks.save(task);
    }

    @Transactional
    public void taskFinished(UUID workflowId, int revision, String taskId, boolean successful) {
        WorkflowRunEntity run = requireCurrentRunningRevision(workflowId, revision);
        WorkflowTaskEntity task = requireTask(run.getId(), revision, taskId);
        if (successful) task.succeed(clock.instant()); else task.fail(clock.instant());
        tasks.save(task);
    }

    public void registerActiveExecution(UUID workflowId, int revision,
                                        bhavana.agenticsdlc.platform.workflow.execution.CancellationToken token,
                                        Runnable rollback) {
        WorkflowRunEntity run = requireRun(workflowId);
        if (run.getStatus() != WorkflowStatus.RUNNING || run.getCurrentRevision() != revision) {
            throw new IllegalStateException("Only the current running revision can execute");
        }
        active.register(workflowId, revision, token, rollback);
    }

    @Transactional
    public void safeStop(UUID workflowId) {
        WorkflowRunEntity run = requireRun(workflowId);
        Instant now = clock.instant();
        run.transitionTo(WorkflowStatus.CANCELLED, now);
        runs.save(run);
        List<WorkflowTaskEntity> current = currentTasks(run);
        current.forEach(task -> task.cancel(now));
        tasks.saveAll(current);
        active.safeStop(workflowId, run.getCurrentRevision());
        run.transitionTo(WorkflowStatus.ROLLED_BACK, clock.instant());
        runs.save(run);
    }

    private RevisionImpactPlan createRevision(WorkflowRunEntity run, String requirement,
                                              RepositoryManifest repository, Set<String> changedTaskIds) {
        Instant now = clock.instant();
        List<WorkflowTaskEntity> previousTasks = currentTasks(run);
        Map<String, TaskStatus> previous = new HashMap<>();
        previousTasks.forEach(task -> previous.put(task.getTaskId(), task.getStatus()));
        RevisionImpactPlan impact = planner.plan(graph, previous, changedTaskIds);
        previousTasks.stream()
                .filter(task -> impact.invalidatedTaskIds().contains(task.getTaskId()))
                .filter(task -> task.getStatus().satisfiesDependency())
                .forEach(task -> task.invalidate(now));
        tasks.saveAll(previousTasks);

        int nextRevision = run.getCurrentRevision() + 1;
        revisions.save(new WorkflowRevisionEntity(run.getId(), nextRevision,
                hash(requirement), repository.rootHash(), now));
        tasks.saveAll(graph.tasks().values().stream()
                .map(definition -> new WorkflowTaskEntity(run.getId(), nextRevision, definition,
                        impact.nextRevisionStatuses().get(definition.id()))).toList());
        run.beginRevision(nextRevision, now);
        runs.save(run);
        return impact;
    }

    private List<WorkflowTaskEntity> currentTasks(WorkflowRunEntity run) {
        return tasks.findByWorkflowIdAndWorkflowRevisionOrderByTaskId(run.getId(), run.getCurrentRevision());
    }

    private WorkflowRunEntity requireRun(UUID workflowId) {
        return runs.findById(workflowId).orElseThrow(() -> new NoSuchElementException("Workflow not found"));
    }

    private WorkflowRunEntity requireCurrentRunningRevision(UUID workflowId, int revision) {
        WorkflowRunEntity run = requireRun(workflowId);
        if (run.getStatus() != WorkflowStatus.RUNNING || run.getCurrentRevision() != revision) {
            throw new IllegalStateException("Task update targets a stale or stopped workflow revision");
        }
        return run;
    }

    private WorkflowTaskEntity requireTask(UUID workflowId, int revision, String taskId) {
        return tasks.findById(new WorkflowTaskEntity.Key(workflowId, revision, taskId))
                .orElseThrow(() -> new NoSuchElementException("Workflow task not found"));
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Requirement must not be blank");
        return hashes.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
