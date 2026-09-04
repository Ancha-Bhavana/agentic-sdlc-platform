package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.repository.RepositoryManifest;
import bhavana.agenticsdlc.platform.workflow.LifecycleGraphFactory;
import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistentWorkflowCoordinatorTest {
    private final WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
    private final WorkflowRevisionRepository revisions = mock(WorkflowRevisionRepository.class);
    private final WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
    private final ActiveWorkflowRegistry active = new ActiveWorkflowRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final WorkflowGraph graph = new LifecycleGraphFactory().create();

    @Test void clarificationCreatesRevisionAndSelectivelyReusesUnaffectedTasks() {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = running(id);
        run.transitionTo(WorkflowStatus.AWAITING_CLARIFICATION, clock.instant());
        List<WorkflowTaskEntity> previous = completedTasks(id, 1);
        when(runs.findById(id)).thenReturn(Optional.of(run));
        when(tasks.findByWorkflowIdAndWorkflowRevisionOrderByTaskId(id, 1)).thenReturn(previous);
        PersistentWorkflowCoordinator coordinator = coordinator();

        RevisionImpactPlan impact = coordinator.clarify(id, "Clarified requirement", manifest());

        assertThat(run.getCurrentRevision()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(impact.reusedTaskIds()).containsExactly("understand");
        assertThat(previous).filteredOn(task -> task.getTaskId().equals("understand"))
                .allMatch(task -> task.getStatus() == TaskStatus.SUCCEEDED);
        assertThat(previous).filteredOn(task -> !task.getTaskId().equals("understand"))
                .allMatch(task -> task.getStatus() == TaskStatus.INVALIDATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<WorkflowTaskEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(tasks, times(2)).saveAll(captor.capture());
        List<WorkflowTaskEntity> next = new ArrayList<>();
        captor.getAllValues().get(1).forEach(next::add);
        assertThat(next).filteredOn(task -> task.getTaskId().equals("understand"))
                .allMatch(task -> task.getStatus() == TaskStatus.REUSED);
        assertThat(next).filteredOn(task -> task.getTaskId().equals("ambiguity"))
                .allMatch(task -> task.getStatus() == TaskStatus.PENDING);
        verify(revisions).save(argThat(revision -> revision.getRevision() == 2));
    }

    @Test void safeStopPersistsCancellationCancelsProcessAndRollsBackWorkspace() {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = running(id);
        List<WorkflowTaskEntity> current = pendingTasks(id, 1);
        when(runs.findById(id)).thenReturn(Optional.of(run));
        when(tasks.findByWorkflowIdAndWorkflowRevisionOrderByTaskId(id, 1)).thenReturn(current);
        CancellationToken token = new CancellationToken();
        AtomicInteger rollbacks = new AtomicInteger();
        PersistentWorkflowCoordinator coordinator = coordinator();
        coordinator.registerActiveExecution(id, 1, token, rollbacks::incrementAndGet);

        coordinator.safeStop(id);

        assertThat(run.getStatus()).isEqualTo(WorkflowStatus.ROLLED_BACK);
        assertThat(token.isCancelled()).isTrue();
        assertThat(rollbacks).hasValue(1);
        assertThat(current).allMatch(task -> task.getStatus() == TaskStatus.CANCELLED);
        verify(runs, times(2)).save(run);
    }

    private PersistentWorkflowCoordinator coordinator() {
        return new PersistentWorkflowCoordinator(runs, revisions, tasks, graph,
                active, clock);
    }

    private WorkflowRunEntity running(UUID id) {
        WorkflowRunEntity run = new WorkflowRunEntity(id, "correlation-" + id, clock.instant());
        run.transitionTo(WorkflowStatus.RUNNING, clock.instant());
        return run;
    }

    private List<WorkflowTaskEntity> completedTasks(UUID id, int revision) {
        List<WorkflowTaskEntity> result = pendingTasks(id, revision);
        result.forEach(task -> {
            task.start(clock.instant(), Duration.ofSeconds(30));
            task.succeed(clock.instant());
        });
        return result;
    }

    private List<WorkflowTaskEntity> pendingTasks(UUID id, int revision) {
        return graph.tasks().values().stream()
                .map(definition -> new WorkflowTaskEntity(id, revision, definition, TaskStatus.PENDING))
                .toList();
    }

    private RepositoryManifest manifest() {
        return new RepositoryManifest(Map.of(), "a".repeat(64));
    }

}
