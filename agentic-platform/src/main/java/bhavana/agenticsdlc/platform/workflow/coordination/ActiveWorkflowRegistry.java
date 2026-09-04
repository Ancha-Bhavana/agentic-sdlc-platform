package bhavana.agenticsdlc.platform.workflow.coordination;

import bhavana.agenticsdlc.platform.workflow.execution.CancellationToken;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActiveWorkflowRegistry {
    private final ConcurrentHashMap<UUID, ActiveExecution> executions = new ConcurrentHashMap<>();

    public void register(UUID workflowId, int revision, CancellationToken token, Runnable rollback) {
        if (workflowId == null || revision < 1 || token == null || rollback == null) {
            throw new IllegalArgumentException("Complete active workflow registration required");
        }
        if (executions.putIfAbsent(workflowId, new ActiveExecution(revision, token, rollback)) != null) {
            throw new IllegalStateException("Workflow already has an active execution");
        }
    }

    public void complete(UUID workflowId, int revision) {
        executions.computeIfPresent(workflowId, (id, execution) ->
                execution.revision() == revision ? null : execution);
    }

    public void safeStop(UUID workflowId, int revision) {
        ActiveExecution execution = executions.get(workflowId);
        if (execution == null || execution.revision() != revision) {
            throw new IllegalStateException("No active execution for current workflow revision");
        }
        execution.token().cancel();
        execution.rollback().run();
        executions.remove(workflowId, execution);
    }

    public boolean isActive(UUID workflowId) { return executions.containsKey(workflowId); }

    private record ActiveExecution(int revision, CancellationToken token, Runnable rollback) { }
}
