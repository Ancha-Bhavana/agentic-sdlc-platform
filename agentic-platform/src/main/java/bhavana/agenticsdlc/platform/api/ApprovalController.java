package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.governance.*;
import bhavana.agenticsdlc.platform.workflow.domain.GateType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/workflows/{workflowId}/approvals")
public class ApprovalController {
    private final ApprovalService approvals;
    public ApprovalController(ApprovalService approvals) { this.approvals = approvals; }

    @PostMapping("/change")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Approve or reject the exact reviewed change revision")
    public ApprovalView change(@PathVariable UUID workflowId, @Valid @RequestBody ApprovalRequest request,
                               Authentication authentication, HttpServletRequest servletRequest) {
        return decide(workflowId, GateType.CHANGE_APPROVAL, request, authentication, servletRequest);
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('RELEASE_APPROVER')")
    @Operation(summary = "Grant or reject final release readiness for the exact reviewed revision")
    public ApprovalView release(@PathVariable UUID workflowId, @Valid @RequestBody ApprovalRequest request,
                                Authentication authentication, HttpServletRequest servletRequest) {
        return decide(workflowId, GateType.RELEASE_APPROVAL, request, authentication, servletRequest);
    }

    private ApprovalView decide(UUID workflowId, GateType gate, ApprovalRequest request,
                                Authentication authentication, HttpServletRequest servletRequest) {
        ApprovalEntity result = approvals.decide(workflowId, request.revision(), gate, request.decision(),
                request.artifactHashes(), request.reason(), AuthenticatedActor.from(authentication),
                servletRequest.getAttribute(CorrelationIdFilter.ATTRIBUTE).toString());
        return ApprovalView.from(result);
    }

    public record ApprovalRequest(@Min(1) int revision, @NotNull ApprovalDecision decision,
                                  @NotEmpty Map<@NotBlank String, @Pattern(regexp = "[0-9a-f]{64}") String> artifactHashes,
                                  @NotBlank @Size(max = 1000) String reason) { }
    public record ApprovalView(UUID id, UUID workflowId, int revision, String gate, String decision,
                               String actor, String role, String artifactHash, boolean valid) {
        static ApprovalView from(ApprovalEntity value) {
            return new ApprovalView(value.getId(), value.getWorkflowId(), value.getWorkflowRevision(),
                    value.getGateType().name(), value.getDecision().name(), value.getActor(),
                    value.getActorRole(), value.getArtifactHash(), value.isValid());
        }
    }
}
