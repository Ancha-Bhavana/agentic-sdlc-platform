package bhavana.agenticsdlc.shortener;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.net.URI;

@RestControllerAdvice
final class ApiExceptionHandler {
    @ExceptionHandler(ShortUrlNotFoundException.class)
    ProblemDetail notFound(ShortUrlNotFoundException failure) {
        return problem(HttpStatus.NOT_FOUND, "Short URL unavailable", failure.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class})
    ProblemDetail invalid(Exception failure) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", failure.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException failure) {
        return problem(HttpStatus.CONFLICT, "Short-code allocation failed", failure.getMessage());
    }
    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail value = ProblemDetail.forStatusAndDetail(status, detail);
        value.setTitle(title); value.setType(URI.create("urn:agentic-sdlc:url-shortener:" + status.value()));
        return value;
    }
}
