package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.workflow.persistence.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowApplicationService workflows;
    public WorkflowController(WorkflowApplicationService workflows) { this.workflows = workflows; }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Submit an asynchronous governed engineering workflow")
    public ResponseEntity<WorkflowView> submit(@Valid @RequestBody SubmitWorkflowRequest request,
                                               Authentication authentication, HttpServletRequest servletRequest) {
        WorkflowRunEntity run = workflows.submit(request.requirement(), request.repositoryPath(),
                correlation(servletRequest), AuthenticatedActor.from(authentication));
        return ResponseEntity.accepted().location(URI.create("/api/workflows/" + run.getId()))
                .body(WorkflowView.from(run, workflows.currentTasks(run.getId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Inspect workflow and current revision task states")
    public WorkflowView get(@PathVariable UUID id) {
        WorkflowRunEntity run = workflows.require(id);
        return WorkflowView.from(run, workflows.currentTasks(id));
    }

    @PostMapping("/{id}/clarifications")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Clarify a paused workflow and create a selectively replanned revision")
    public WorkflowView clarify(@PathVariable UUID id, @Valid @RequestBody ClarificationRequest request,
                                Authentication authentication, HttpServletRequest servletRequest) {
        WorkflowRunEntity run = workflows.clarify(id, request.requirement(), request.repositoryPath(),
                correlation(servletRequest), AuthenticatedActor.from(authentication));
        return WorkflowView.from(run, workflows.currentTasks(id));
    }

    @PostMapping("/{id}/safe-stop")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Cancel active work and roll the workspace back")
    public WorkflowView safeStop(@PathVariable UUID id, Authentication authentication,
                                 HttpServletRequest servletRequest) {
        WorkflowRunEntity run = workflows.safeStop(id, correlation(servletRequest),
                AuthenticatedActor.from(authentication));
        return WorkflowView.from(run, workflows.currentTasks(id));
    }

    private String correlation(HttpServletRequest request) {
        return request.getAttribute(CorrelationIdFilter.ATTRIBUTE).toString();
    }

    public record SubmitWorkflowRequest(
            @NotBlank @Size(max = 32_000) String requirement,
            @NotBlank @Size(max = 1000) String repositoryPath) { }
    public record ClarificationRequest(
            @NotBlank @Size(max = 32_000) String requirement,
            @NotBlank @Size(max = 1000) String repositoryPath) { }
    public record WorkflowView(UUID id, String correlationId, String status, int revision,
                               List<TaskView> tasks) {
        static WorkflowView from(WorkflowRunEntity run, List<WorkflowTaskEntity> tasks) {
            return new WorkflowView(run.getId(), run.getCorrelationId(), run.getStatus().name(),
                    run.getCurrentRevision(), tasks.stream().map(TaskView::from).toList());
        }
    }
    public record TaskView(String id, String type, String status, int attempt) {
        static TaskView from(WorkflowTaskEntity task) {
            return new TaskView(task.getTaskId(), task.getTaskType().name(), task.getStatus().name(), task.getAttempt());
        }
    }
}
