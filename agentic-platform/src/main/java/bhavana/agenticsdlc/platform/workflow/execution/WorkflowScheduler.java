package bhavana.agenticsdlc.platform.workflow.execution;

import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import bhavana.agenticsdlc.platform.workflow.domain.TaskDefinition;
import bhavana.agenticsdlc.platform.workflow.domain.TaskStatus;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import bhavana.agenticsdlc.platform.workflow.graph.WorkflowGraph;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class WorkflowScheduler {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(5);
    private final BackoffPolicy backoffPolicy;

    public WorkflowScheduler(BackoffPolicy backoffPolicy) {
        this.backoffPolicy = backoffPolicy;
    }

    public WorkflowExecutionReport execute(WorkflowGraph graph, TaskRunner runner,
                                           GateEvaluator gates, Duration workflowTimeout,
                                           CancellationToken cancellationToken) {
        long started = System.nanoTime();
        long workflowDeadline = started + workflowTimeout.toNanos();
        Map<String, TaskStatus> statuses = new HashMap<>();
        Map<String, Integer> attempts = new HashMap<>();
        Map<String, String> summaries = new HashMap<>();
        Map<String, Long> retryAt = new HashMap<>();
        Map<Future<CompletedTask>, RunningTask> running = new HashMap<>();
        Map<String, String> fallbackFor = new HashMap<>();
        Set<String> forcedReady = new HashSet<>();
        Set<String> fallbackTargets = graph.tasks().values().stream()
                .map(TaskDefinition::fallbackTaskId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        graph.tasks().keySet().forEach(id -> statuses.put(id, TaskStatus.PENDING));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletionService<CompletedTask> completion = new ExecutorCompletionService<>(executor);
            while (true) {
                long now = System.nanoTime();
                if (cancellationToken.isCancelled() || now >= workflowDeadline) {
                    cancellationToken.cancel();
                    cancelRunning(running);
                    cancelUnfinished(statuses);
                    return report(WorkflowStatus.CANCELLED, statuses, attempts, summaries, started);
                }

                WorkflowStatus gateStatus = scheduleReady(graph, runner, gates, cancellationToken,
                        completion, running, statuses, attempts, summaries, retryAt,
                        fallbackFor, forcedReady, fallbackTargets, now);
                if (gateStatus != null) {
                    cancelRunning(running);
                    if (gateStatus == WorkflowStatus.REJECTED) {
                        cancelUnfinished(statuses);
                    }
                    return report(gateStatus, statuses, attempts, summaries, started);
                }

                expireTimedOutTasks(running, statuses, attempts, summaries, retryAt,
                        fallbackFor, forcedReady, graph, now);

                if (allDependenciesSatisfied(graph, statuses, fallbackFor)) {
                    return report(WorkflowStatus.COMPLETED, statuses, attempts, summaries, started);
                }

                Future<CompletedTask> completedFuture = completion.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
                if (completedFuture != null) {
                    RunningTask tracked = running.remove(completedFuture);
                    if (tracked != null) {
                        WorkflowStatus completionStatus = processCompletion(completedFuture, tracked, graph, gates, statuses, attempts,
                                summaries, retryAt, fallbackFor, forcedReady);
                        if (completionStatus != null) {
                            cancelRunning(running);
                            if (completionStatus == WorkflowStatus.REJECTED) {
                                cancelUnfinished(statuses);
                            }
                            return report(completionStatus, statuses, attempts, summaries, started);
                        }
                        // Re-evaluate completion, retries, fallbacks, and newly ready dependants
                        // before deciding that the graph is unable to make progress.
                        continue;
                    }
                }

                if (running.isEmpty() && noTaskCanProgress(graph, statuses, retryAt, forcedReady, fallbackFor, now)) {
                    cancelBlockedTasks(statuses);
                    return report(WorkflowStatus.FAILED, statuses, attempts, summaries, started);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancellationToken.cancel();
            cancelRunning(running);
            cancelUnfinished(statuses);
            return report(WorkflowStatus.CANCELLED, statuses, attempts, summaries, started);
        }
    }

    private WorkflowStatus scheduleReady(
            WorkflowGraph graph, TaskRunner runner, GateEvaluator gates, CancellationToken token,
            CompletionService<CompletedTask> completion, Map<Future<CompletedTask>, RunningTask> running,
            Map<String, TaskStatus> statuses, Map<String, Integer> attempts, Map<String, String> summaries,
            Map<String, Long> retryAt, Map<String, String> fallbackFor, Set<String> forcedReady,
            Set<String> fallbackTargets, long now) {
        for (TaskDefinition task : graph.tasks().values()) {
            if (statuses.get(task.id()) != TaskStatus.PENDING || retryAt.getOrDefault(task.id(), 0L) > now) {
                continue;
            }
            if (fallbackTargets.contains(task.id()) && !forcedReady.contains(task.id())) {
                continue;
            }
            if (!forcedReady.contains(task.id()) && !dependenciesSatisfied(task, statuses, fallbackFor)) {
                continue;
            }
            GateEvaluator.GateDecision decision = evaluate(gates, task, task.entryGate(), GateEvaluator.GatePhase.ENTRY);
            if (decision == GateEvaluator.GateDecision.WAIT) {
                statuses.put(task.id(), TaskStatus.WAITING_AT_GATE);
                return statusForGate(task.entryGate());
            }
            if (decision == GateEvaluator.GateDecision.REJECT) {
                statuses.put(task.id(), TaskStatus.CANCELLED);
                summaries.put(task.id(), "Entry gate rejected execution");
                return WorkflowStatus.REJECTED;
            }
            forcedReady.remove(task.id());
            retryAt.remove(task.id());
            int attempt = attempts.merge(task.id(), 1, Integer::sum);
            statuses.put(task.id(), TaskStatus.RUNNING);
            Future<CompletedTask> future = completion.submit(() -> runTask(runner, task, attempt, token));
            running.put(future, new RunningTask(task, future, now));
        }
        return null;
    }

    private CompletedTask runTask(TaskRunner runner, TaskDefinition task, int attempt, CancellationToken token) {
        try {
            token.throwIfCancelled();
            return new CompletedTask(task, runner.execute(new TaskExecutionContext(task, attempt, token)));
        } catch (CancellationException cancelled) {
            return new CompletedTask(task, TaskExecutionResult.failure("Task cancelled", false));
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            return new CompletedTask(task, TaskExecutionResult.failure(message, true));
        }
    }

    private WorkflowStatus processCompletion(
            Future<CompletedTask> future, RunningTask tracked, WorkflowGraph graph, GateEvaluator gates,
            Map<String, TaskStatus> statuses, Map<String, Integer> attempts, Map<String, String> summaries,
            Map<String, Long> retryAt, Map<String, String> fallbackFor, Set<String> forcedReady) {
        try {
            CompletedTask completed = future.get();
            TaskExecutionResult result = completed.result();
            summaries.put(completed.definition().id(), result.summary());
            if (result.successful()) {
                GateEvaluator.GateDecision exit = evaluate(gates, completed.definition(),
                        completed.definition().exitGate(), GateEvaluator.GatePhase.EXIT);
                statuses.put(completed.definition().id(), exit == GateEvaluator.GateDecision.PASS
                        ? TaskStatus.SUCCEEDED : exit == GateEvaluator.GateDecision.WAIT
                        ? TaskStatus.WAITING_AT_GATE : TaskStatus.CANCELLED);
                String unusedFallback = completed.definition().fallbackTaskId();
                if (exit == GateEvaluator.GateDecision.PASS && unusedFallback != null
                        && statuses.get(unusedFallback) == TaskStatus.PENDING) {
                    statuses.put(unusedFallback, TaskStatus.REUSED);
                    summaries.put(unusedFallback, "Fallback was not required");
                }
                if (exit == GateEvaluator.GateDecision.WAIT) {
                    return statusForGate(completed.definition().exitGate());
                }
                if (exit == GateEvaluator.GateDecision.REJECT) {
                    summaries.put(completed.definition().id(), "Exit gate rejected completed task");
                    return WorkflowStatus.REJECTED;
                }
                return null;
            }
            handleFailure(completed.definition(), result.retryable(), graph, statuses, attempts,
                    summaries, retryAt, fallbackFor, forcedReady, System.nanoTime());
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            statuses.put(tracked.definition().id(), TaskStatus.CANCELLED);
            return WorkflowStatus.CANCELLED;
        } catch (ExecutionException failure) {
            summaries.put(tracked.definition().id(), failure.getCause().toString());
            handleFailure(tracked.definition(), true, graph, statuses, attempts,
                    summaries, retryAt, fallbackFor, forcedReady, System.nanoTime());
            return null;
        } catch (CancellationException ignored) {
            // A timeout path has already recorded the resulting state.
            return null;
        }
    }

    private void handleFailure(TaskDefinition task, boolean retryable, WorkflowGraph graph,
                               Map<String, TaskStatus> statuses, Map<String, Integer> attempts,
                               Map<String, String> summaries, Map<String, Long> retryAt,
                               Map<String, String> fallbackFor, Set<String> forcedReady, long now) {
        int attempt = attempts.getOrDefault(task.id(), 0);
        if (retryable && attempt < task.maximumAttempts()) {
            statuses.put(task.id(), TaskStatus.PENDING);
            retryAt.put(task.id(), now + backoffPolicy.delayAfterFailure(attempt).toNanos());
        } else {
            statuses.put(task.id(), TaskStatus.FAILED);
            if (task.fallbackTaskId() != null && graph.tasks().containsKey(task.fallbackTaskId())) {
                fallbackFor.put(task.id(), task.fallbackTaskId());
                forcedReady.add(task.fallbackTaskId());
                summaries.put(task.id(), summaries.getOrDefault(task.id(), "Task failed")
                        + "; fallback scheduled: " + task.fallbackTaskId());
            }
        }
    }

    private void expireTimedOutTasks(Map<Future<CompletedTask>, RunningTask> running,
                                     Map<String, TaskStatus> statuses, Map<String, Integer> attempts,
                                     Map<String, String> summaries, Map<String, Long> retryAt,
                                     Map<String, String> fallbackFor, Set<String> forcedReady,
                                     WorkflowGraph graph, long now) {
        for (RunningTask task : Set.copyOf(running.values())) {
            if (now - task.startedAtNanos() >= task.definition().timeout().toNanos()) {
                task.future().cancel(true);
                running.remove(task.future());
                summaries.put(task.definition().id(), "Task timed out after " + task.definition().timeout());
                handleFailure(task.definition(), true, graph, statuses, attempts, summaries,
                        retryAt, fallbackFor, forcedReady, now);
            }
        }
    }

    private GateEvaluator.GateDecision evaluate(GateEvaluator gates, TaskDefinition task,
                                                GateType gate, GateEvaluator.GatePhase phase) {
        return gate == GateType.NONE ? GateEvaluator.GateDecision.PASS : gates.evaluate(task, gate, phase);
    }

    private boolean dependenciesSatisfied(TaskDefinition task, Map<String, TaskStatus> statuses,
                                          Map<String, String> fallbackFor) {
        return task.dependencies().stream().allMatch(id -> dependencySatisfied(id, statuses, fallbackFor));
    }

    private boolean dependencySatisfied(String id, Map<String, TaskStatus> statuses,
                                        Map<String, String> fallbackFor) {
        if (statuses.get(id) != null && statuses.get(id).satisfiesDependency()) {
            return true;
        }
        String fallback = fallbackFor.get(id);
        return fallback != null && statuses.get(fallback) != null && statuses.get(fallback).satisfiesDependency();
    }

    private boolean allDependenciesSatisfied(WorkflowGraph graph, Map<String, TaskStatus> statuses,
                                             Map<String, String> fallbackFor) {
        return graph.tasks().keySet().stream().allMatch(id ->
                statuses.get(id).satisfiesDependency()
                        || (statuses.get(id) == TaskStatus.FAILED && fallbackFor.containsKey(id)
                        && dependencySatisfied(id, statuses, fallbackFor)));
    }

    private boolean noTaskCanProgress(WorkflowGraph graph, Map<String, TaskStatus> statuses,
                                      Map<String, Long> retryAt, Set<String> forcedReady,
                                      Map<String, String> fallbackFor, long now) {
        return graph.tasks().values().stream()
                .filter(task -> statuses.get(task.id()) == TaskStatus.PENDING)
                .noneMatch(task -> retryAt.getOrDefault(task.id(), 0L) > now
                        || forcedReady.contains(task.id())
                        || dependenciesSatisfied(task, statuses, fallbackFor));
    }

    private WorkflowStatus statusForGate(GateType gate) {
        return gate == GateType.CLARIFICATION
                ? WorkflowStatus.AWAITING_CLARIFICATION : WorkflowStatus.AWAITING_APPROVAL;
    }

    private void cancelRunning(Map<Future<CompletedTask>, RunningTask> running) {
        running.keySet().forEach(future -> future.cancel(true));
        running.clear();
    }

    private void cancelUnfinished(Map<String, TaskStatus> statuses) {
        statuses.replaceAll((id, status) -> status.isFinished() ? status : TaskStatus.CANCELLED);
    }

    private void cancelBlockedTasks(Map<String, TaskStatus> statuses) {
        statuses.replaceAll((id, status) -> status == TaskStatus.PENDING ? TaskStatus.CANCELLED : status);
    }

    private WorkflowExecutionReport report(WorkflowStatus status, Map<String, TaskStatus> statuses,
                                           Map<String, Integer> attempts, Map<String, String> summaries,
                                           long started) {
        return new WorkflowExecutionReport(status, statuses, attempts, summaries,
                Duration.ofNanos(System.nanoTime() - started));
    }

    private record CompletedTask(TaskDefinition definition, TaskExecutionResult result) {}
    private record RunningTask(TaskDefinition definition, Future<CompletedTask> future, long startedAtNanos) {}
}
