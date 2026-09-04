package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;
import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import bhavana.agenticsdlc.platform.workflow.domain.TaskType;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSchedulerTest {
    private final WorkflowScheduler scheduler = new WorkflowScheduler(attempt -> Duration.ZERO);

    @Test
    void executesIndependentTasksConcurrentlyAndSynchronizesTheirJoin() throws Exception {
        WorkflowGraph graph = new WorkflowGraph(List.of(
                task("root", Set.of()),
                task("implementation", Set.of("root")),
                task("tests", Set.of("root")),
                task("validation", Set.of("implementation", "tests"))));
        CountDownLatch parallelTasksStarted = new CountDownLatch(2);
        CountDownLatch releaseParallelTasks = new CountDownLatch(1);
        AtomicInteger activeParallelTasks = new AtomicInteger();
        AtomicBoolean joinObservedRunningParent = new AtomicBoolean();

        CompletableFuture<WorkflowExecutionReport> execution = CompletableFuture.supplyAsync(() ->
                scheduler.execute(graph, context -> {
                    String id = context.task().id();
                    if (id.equals("implementation") || id.equals("tests")) {
                        activeParallelTasks.incrementAndGet();
                        parallelTasksStarted.countDown();
                        releaseParallelTasks.await(2, TimeUnit.SECONDS);
                        activeParallelTasks.decrementAndGet();
                    }
                    if (id.equals("validation")) {
                        joinObservedRunningParent.set(activeParallelTasks.get() != 0);
                    }
                    return TaskExecutionResult.success(id + " complete");
                }, GateEvaluator.allowAll(), Duration.ofSeconds(5), new CancellationToken()));

        assertThat(parallelTasksStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(activeParallelTasks.get()).isEqualTo(2);
        releaseParallelTasks.countDown();

        WorkflowExecutionReport report = execution.get(2, TimeUnit.SECONDS);
        assertThat(report.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(joinObservedRunningParent).isFalse();
        assertThat(report.taskStatuses()).allSatisfy((id, status) ->
                assertThat(status).isEqualTo(TaskStatus.SUCCEEDED));
    }

    @Test
    void retriesOnlyUntilTheConfiguredBound() {
        WorkflowGraph graph = new WorkflowGraph(List.of(task("unstable", Set.of(), 3)));
        AtomicInteger calls = new AtomicInteger();

        WorkflowExecutionReport report = scheduler.execute(graph, context ->
                        calls.incrementAndGet() < 3
                                ? TaskExecutionResult.failure("temporary", true)
                                : TaskExecutionResult.success("recovered"),
                GateEvaluator.allowAll(), Duration.ofSeconds(2), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(report.attempts()).containsEntry("unstable", 3);
        assertThat(report.summaries()).containsEntry("unstable", "recovered");
    }

    @Test
    void stopsAfterExhaustingRetriesAndPropagatesFailure() {
        WorkflowGraph graph = new WorkflowGraph(List.of(
                task("failing", Set.of(), 2), task("dependent", Set.of("failing"))));

        WorkflowExecutionReport report = scheduler.execute(graph,
                context -> TaskExecutionResult.failure("still broken", true),
                GateEvaluator.allowAll(), Duration.ofSeconds(2), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(report.attempts()).containsEntry("failing", 2).doesNotContainKey("dependent");
        assertThat(report.taskStatuses()).containsEntry("failing", TaskStatus.FAILED)
                .containsEntry("dependent", TaskStatus.CANCELLED);
    }

    @Test
    void executesDeclaredFallbackAndThenUnblocksDependentWork() {
        TaskDefinition primary = new TaskDefinition("primary", TaskType.IMPLEMENTATION, Set.of(),
                GateType.NONE, GateType.NONE, 1, Duration.ofSeconds(1), "fallback");
        WorkflowGraph graph = new WorkflowGraph(List.of(primary, task("fallback", Set.of()),
                task("dependent", Set.of("primary"))));

        WorkflowExecutionReport report = scheduler.execute(graph, context ->
                        context.task().id().equals("primary")
                                ? TaskExecutionResult.failure("provider unavailable", false)
                                : TaskExecutionResult.success("completed " + context.task().id()),
                GateEvaluator.allowAll(), Duration.ofSeconds(2), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(report.taskStatuses()).containsEntry("primary", TaskStatus.FAILED)
                .containsEntry("fallback", TaskStatus.SUCCEEDED)
                .containsEntry("dependent", TaskStatus.SUCCEEDED);
    }

    @Test
    void pausesAtEntryGateWithoutExecutingTheTask() {
        TaskDefinition gated = new TaskDefinition("release", TaskType.RELEASE_READINESS, Set.of(),
                GateType.RELEASE_APPROVAL, GateType.NONE, 1, Duration.ofSeconds(1));
        AtomicBoolean executed = new AtomicBoolean();

        WorkflowExecutionReport report = scheduler.execute(new WorkflowGraph(List.of(gated)), context -> {
            executed.set(true);
            return TaskExecutionResult.success("unexpected");
        }, (task, gate, phase) -> GateEvaluator.GateDecision.WAIT,
                Duration.ofSeconds(1), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
        assertThat(report.taskStatuses()).containsEntry("release", TaskStatus.WAITING_AT_GATE);
        assertThat(executed).isFalse();
    }

    @Test
    void pausesAtExitGateOnlyAfterSuccessfulExecution() {
        TaskDefinition gated = new TaskDefinition("clarify", TaskType.AMBIGUITY_DETECTION, Set.of(),
                GateType.NONE, GateType.CLARIFICATION, 1, Duration.ofSeconds(1));

        WorkflowExecutionReport report = scheduler.execute(new WorkflowGraph(List.of(gated)),
                context -> TaskExecutionResult.success("questions generated"),
                (task, gate, phase) -> GateEvaluator.GateDecision.WAIT,
                Duration.ofSeconds(1), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        assertThat(report.taskStatuses()).containsEntry("clarify", TaskStatus.WAITING_AT_GATE);
        assertThat(report.attempts()).containsEntry("clarify", 1);
    }

    @Test
    void workflowDeadlineCancelsRunningAndPendingTasks() {
        WorkflowGraph graph = new WorkflowGraph(List.of(
                task("slow", Set.of(), 1), task("after", Set.of("slow"))));

        WorkflowExecutionReport report = scheduler.execute(graph, context -> {
            while (!context.cancellationToken().isCancelled()) {
                Thread.onSpinWait();
            }
            return TaskExecutionResult.failure("cancelled", false);
        }, GateEvaluator.allowAll(), Duration.ofMillis(40), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(report.taskStatuses().values()).allMatch(TaskStatus::isFinished);
        assertThat(report.duration()).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void taskTimeoutIsRetriedWithinItsBound() {
        TaskDefinition shortTask = new TaskDefinition("short", TaskType.VALIDATION, Set.of(),
                GateType.NONE, GateType.NONE, 2, Duration.ofMillis(20));

        WorkflowExecutionReport report = scheduler.execute(new WorkflowGraph(List.of(shortTask)), context -> {
            Thread.sleep(200);
            return TaskExecutionResult.success("late");
        }, GateEvaluator.allowAll(), Duration.ofSeconds(2), new CancellationToken());

        assertThat(report.status()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(report.attempts()).containsEntry("short", 2);
        assertThat(report.summaries().get("short")).contains("timed out");
    }

    private TaskDefinition task(String id, Set<String> dependencies) {
        return task(id, dependencies, 1);
    }

    private TaskDefinition task(String id, Set<String> dependencies, int maximumAttempts) {
        return new TaskDefinition(id, TaskType.IMPLEMENTATION, dependencies,
                GateType.NONE, GateType.NONE, maximumAttempts, Duration.ofSeconds(1));
    }
}

