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
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void setUp() {
        approvals = mock(ApprovalRepository.class);
        runs = mock(WorkflowRunRepository.class);
        AuditService audit = mock(AuditService.class);
        when(approvals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ApprovalService(approvals, runs, audit, clock);
    }

    @Test void recordsAuthenticatedReleaseApproverAgainstExactRevisionAndArtifacts() {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = awaitingApproval(id);
        when(runs.findById(id)).thenReturn(Optional.of(run));

        ApprovalEntity result = service.decide(id, 1, GateType.RELEASE_APPROVAL,
                ApprovalDecision.APPROVED, Map.of("diff", "a".repeat(64)), "Ready to release",
                new ActorIdentity("release-user", "ROLE_RELEASE_APPROVER"), "correlation");

        assertThat(result.getActor()).isEqualTo("release-user");
        assertThat(result.getArtifactHash()).matches("[0-9a-f]{64}");
        assertThat(run.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test void rejectsWrongRoleAndStaleRevision() {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = awaitingApproval(id);
        when(runs.findById(id)).thenReturn(Optional.of(run));
        Map<String, String> evidence = Map.of("diff", "a".repeat(64));

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
