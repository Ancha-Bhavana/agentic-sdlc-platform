package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.audit.*;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.governance.*;
import bhavana.agenticsdlc.platform.repository.*;
import bhavana.agenticsdlc.platform.scenario.*;
import bhavana.agenticsdlc.platform.workflow.coordination.PersistentWorkflowCoordinator;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.*;

@Service
public class WorkflowApplicationService {
    private final PersistentWorkflowCoordinator coordinator;
    private final WorkflowRunRepository runs;
    private final WorkflowTaskRepository tasks;
    private final GovernancePolicyEngine policies;
    private final ApprovalService approvals;
    private final AuditService audit;
    private final SafePathResolver repositoryPaths;
    private final DeterministicScenarioExecutor scenarios;
    public WorkflowApplicationService(PersistentWorkflowCoordinator coordinator, WorkflowRunRepository runs,
            WorkflowTaskRepository tasks, GovernancePolicyEngine policies, ApprovalService approvals,
            AuditService audit, DeterministicScenarioExecutor scenarios,
            @Value("${agentic-sdlc.repository.approved-root:.}") Path approvedRoot) {
        this.coordinator = coordinator; this.runs = runs; this.tasks = tasks; this.policies = policies;
        this.approvals = approvals; this.audit = audit; this.scenarios = scenarios;
        this.repositoryPaths = new SafePathResolver(approvedRoot);
    }

    public WorkflowRunEntity submit(String requirement, String repository, String correlationId, ActorIdentity actor) {
        return submit(requirement, repository, ScenarioType.BROWNFIELD, correlationId, actor);
    }

    public WorkflowRunEntity submit(String requirement, String repository, ScenarioType scenarioType,
                                    String correlationId, ActorIdentity actor) {
        UUID id = UUID.randomUUID();
        Path admitted = repositoryPaths.admitRepository(Path.of(repository));
        policies.enforceSubmission(id, 1, requirement, admitted);
        WorkflowRunEntity run = coordinator.submit(id, correlationId, requirement,
                new ManifestService().capture(admitted));
        audit.record(id, 1, correlationId, "WORKFLOW_SUBMITTED", actor, "repository=" + admitted);
        scenarios.start(id, 1, scenarioType == null ? ScenarioType.BROWNFIELD : scenarioType,
                requirement, correlationId);
        return run;
    }

    public WorkflowRunEntity clarify(UUID id, String requirement, String repository,
                                     String correlationId, ActorIdentity actor) {
        WorkflowRunEntity current = require(id);
        Path admitted = repositoryPaths.admitRepository(Path.of(repository));
        int nextRevision = current.getCurrentRevision() + 1;
        policies.enforceSubmission(id, nextRevision, requirement, admitted);
        approvals.invalidateForNewRevision(id, actor, correlationId);
        coordinator.clarify(id, requirement, new ManifestService().capture(admitted));
        audit.record(id, nextRevision, correlationId, "CLARIFICATION_SUBMITTED", actor,
                "Created workflow revision " + nextRevision);
        scenarios.resumeAfterClarification(id, nextRevision, requirement, correlationId);
        return require(id);
    }

    public WorkflowRunEntity safeStop(UUID id, String correlationId, ActorIdentity actor) {
        coordinator.safeStop(id);
        WorkflowRunEntity run = require(id);
        audit.record(id, run.getCurrentRevision(), correlationId, "WORKFLOW_SAFE_STOPPED", actor,
                "Execution cancelled and workspace rollback completed");
        return run;
    }

    public WorkflowRunEntity require(UUID id) {
        return runs.findById(id).orElseThrow(() -> new NoSuchElementException("Workflow not found"));
    }

    public List<WorkflowTaskEntity> currentTasks(UUID id) {
        WorkflowRunEntity run = require(id);
        return tasks.findByWorkflowIdAndWorkflowRevisionOrderByTaskId(id, run.getCurrentRevision());
    }
}
