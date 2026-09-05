package bhavana.agenticsdlc.platform.governance;

import bhavana.agenticsdlc.platform.audit.*;
import bhavana.agenticsdlc.platform.audit.AuditService.ActorIdentity;
import bhavana.agenticsdlc.platform.workflow.domain.*;
import bhavana.agenticsdlc.platform.workflow.persistence.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {
    private ApprovalRepository approvals;
    private WorkflowRunRepository runs;
    private ApprovalService service;
    private ContextArtifactRepository artifacts;
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void setUp() {
        approvals = mock(ApprovalRepository.class);
        runs = mock(WorkflowRunRepository.class);
        AuditService audit = mock(AuditService.class);
        artifacts = mock(ContextArtifactRepository.class);
        when(approvals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ApprovalService(approvals, runs, audit, clock,
                mock(bhavana.agenticsdlc.platform.scenario.DeterministicScenarioExecutor.class),
                mock(bhavana.agenticsdlc.platform.observability.WorkflowMetrics.class), artifacts);
    }

    @Test void recordsAuthenticatedReleaseApproverAgainstExactRevisionAndArtifacts() {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = awaitingApproval(id);
        when(runs.findById(id)).thenReturn(Optional.of(run));
        artifact(id, "engineering-outcome", "a".repeat(64));

        ApprovalEntity result = service.decide(id, 1, GateType.RELEASE_APPROVAL,
                ApprovalDecision.APPROVED, Map.of("engineering-outcome", "a".repeat(64)), "Ready to release",
                new ActorIdentity("release-user", "ROLE_RELEASE_APPROVER"), "correlation");

        assertThat(result.getActor()).isEqualTo("release-user");
        assertThat(result.getArtifactHash()).matches("[0-9a-f]{64}");
        assertThat(run.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test void rejectsWrongRoleAndStaleRevision() {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = awaitingApproval(id);
        when(runs.findById(id)).thenReturn(Optional.of(run));
        artifact(id, "engineering-outcome", "a".repeat(64));
        Map<String, String> evidence = Map.of("engineering-outcome", "a".repeat(64));

        assertThatThrownBy(() -> service.decide(id, 1, GateType.RELEASE_APPROVAL,
                ApprovalDecision.APPROVED, evidence, "reason",
                new ActorIdentity("ordinary-approver", "ROLE_APPROVER"), "correlation"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.decide(id, 2, GateType.CHANGE_APPROVAL,
                ApprovalDecision.APPROVED, evidence, "reason",
                new ActorIdentity("approver", "ROLE_APPROVER"), "correlation"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("stale");
        verify(approvals, never()).save(any());
    }

    @Test void rejectsAStaleArtifactHash() {
        UUID id = UUID.randomUUID();
        when(runs.findById(id)).thenReturn(Optional.of(awaitingApproval(id)));
        artifact(id, "engineering-plan", "b".repeat(64));
        assertThatThrownBy(() -> service.decide(id, 1, GateType.CHANGE_APPROVAL,
                ApprovalDecision.APPROVED, Map.of("engineering-plan", "a".repeat(64)), "reviewed",
                new ActorIdentity("approver", "ROLE_APPROVER"), "correlation"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale");
    }

    @Test void rejectsCurrentButIrrelevantEvidenceForEachGate() {
        UUID id = UUID.randomUUID();
        when(runs.findById(id)).thenReturn(Optional.of(awaitingApproval(id)));
        artifact(id, "repository-analysis", "a".repeat(64));

        assertThatThrownBy(() -> service.decide(id, 1, GateType.CHANGE_APPROVAL,
                ApprovalDecision.APPROVED, Map.of("repository-analysis", "a".repeat(64)), "reviewed",
                new ActorIdentity("approver", "ROLE_APPROVER"), "correlation"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("engineering-plan");

        assertThatThrownBy(() -> service.decide(id, 1, GateType.RELEASE_APPROVAL,
                ApprovalDecision.APPROVED, Map.of("repository-analysis", "a".repeat(64)), "reviewed",
                new ActorIdentity("release-user", "ROLE_RELEASE_APPROVER"), "correlation"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("engineering-outcome");
        verify(approvals, never()).save(any());
    }

    private void artifact(UUID id, String key, String hash) {
        ContextArtifactEntity value = mock(ContextArtifactEntity.class);
        when(value.getContentHash()).thenReturn(hash);
        when(artifacts.findFirstByWorkflowIdAndWorkflowRevisionAndArtifactKeyOrderByArtifactVersionDesc(id, 1, key))
                .thenReturn(Optional.of(value));
    }

    @Test void invalidatesEveryPreviouslyValidApprovalAfterRevisionChange() {
        UUID id = UUID.randomUUID();
        ApprovalEntity old = new ApprovalEntity(id, 1, GateType.CHANGE_APPROVAL, "a".repeat(64),
                ApprovalDecision.APPROVED, "approver", "ROLE_APPROVER", "reason", clock.instant());
        when(approvals.findByWorkflowIdAndValidTrue(id)).thenReturn(List.of(old));

        service.invalidateForNewRevision(id, new ActorIdentity("operator", "ROLE_OPERATOR"), "correlation");

        assertThat(old.isValid()).isFalse();
        verify(approvals).saveAll(List.of(old));
    }

    private WorkflowRunEntity awaitingApproval(UUID id) {
        WorkflowRunEntity run = new WorkflowRunEntity(id, "correlation", clock.instant());
        run.transitionTo(WorkflowStatus.RUNNING, clock.instant());
        run.transitionTo(WorkflowStatus.AWAITING_APPROVAL, clock.instant());
        return run;
    }
}
