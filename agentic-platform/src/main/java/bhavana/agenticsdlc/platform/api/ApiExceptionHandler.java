package bhavana.agenticsdlc.platform.api;

import bhavana.agenticsdlc.platform.governance.PolicyViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.net.URI;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ProblemDetail> missing(NoSuchElementException failure) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", failure.getMessage());
    }
    @ExceptionHandler(PolicyViolationException.class)
    ResponseEntity<ProblemDetail> policy(PolicyViolationException failure) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Policy blocked operation", failure.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ProblemDetail> invalid(Exception failure) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> conflict(IllegalStateException failure) {
        return problem(HttpStatus.CONFLICT, "Workflow state conflict", failure.getMessage());
    }
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ProblemDetail> forbidden(SecurityException failure) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", failure.getMessage());
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:agentic-sdlc:problem:" + status.value()));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
