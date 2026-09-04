package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.config.SecurityConfiguration;
import bhavana.agenticsdlc.platform.workflow.domain.WorkflowStatus;
import bhavana.agenticsdlc.platform.workflow.persistence.WorkflowRunEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@WebMvcTest(controllers = WorkflowController.class)
@Import({SecurityConfiguration.class, CorrelationIdFilter.class, ApiExceptionHandler.class})
class WorkflowControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean WorkflowApplicationService workflows;

    @Test void unauthenticatedSubmissionReturnsProblemDetails() throws Exception {
        mvc.perform(post("/api/workflows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Build service\",\"repositoryPath\":\"project\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void approverCannotSubmitOperatorWorkflow() throws Exception {
        mvc.perform(post("/api/workflows").with(httpBasic("approver", "approver-local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Build service\",\"repositoryPath\":\"project\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void operatorSubmissionIsAcceptedAndReturnsCorrelationId() throws Exception {
        UUID id = UUID.randomUUID();
        WorkflowRunEntity run = new WorkflowRunEntity(id, "request-123", Instant.parse("2026-01-01T00:00:00Z"));
        run.transitionTo(WorkflowStatus.RUNNING, Instant.parse("2026-01-01T00:00:00Z"));
        when(workflows.submit(anyString(), anyString(), eq("request-123"), any())).thenReturn(run);
        when(workflows.currentTasks(id)).thenReturn(java.util.List.of());

        mvc.perform(post("/api/workflows").with(httpBasic("operator", "operator-local"))
                        .header(CorrelationIdFilter.HEADER, "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Build service\",\"repositoryPath\":\"project\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "request-123"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }
}
