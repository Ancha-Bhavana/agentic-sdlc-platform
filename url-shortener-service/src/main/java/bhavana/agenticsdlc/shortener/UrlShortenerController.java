package bhavana.agenticsdlc.shortener;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.*;

@RestController
public class UrlShortenerController {
    private final UrlShortenerService service;
    UrlShortenerController(UrlShortenerService service) { this.service = service; }

    @PostMapping("/api/urls")
    @Operation(summary = "Create a short URL")
    ResponseEntity<ShortUrlView> create(@Valid @RequestBody CreateShortUrlRequest request,
                                        HttpServletRequest servletRequest) {
        ShortUrl created = service.create(request.targetUrl(), request.expiresAt());
        ShortUrlView view = ShortUrlView.from(created, baseUrl(servletRequest));
        return ResponseEntity.created(URI.create(view.shortUrl())).body(view);
    }

    @GetMapping("/{code:[A-Za-z0-9]{6,12}}")
    @Operation(summary = "Redirect a short code to its target")
    ResponseEntity<Void> redirect(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND).location(service.resolve(code)).build();
    }

    @GetMapping("/api/urls/{code}")
    @Operation(summary = "Inspect a short URL")
    ShortUrlView get(@PathVariable String code, HttpServletRequest request) {
        return ShortUrlView.from(service.require(code), baseUrl(request));
    }

    @GetMapping("/api/urls/{code}/analytics")
    @Operation(summary = "Read total and UTC daily redirect analytics")
    UrlShortenerService.Analytics analytics(@PathVariable String code,
            @RequestParam(required = false) LocalDate date) {
        return service.analytics(code, date == null ? LocalDate.now(ZoneOffset.UTC) : date);
    }

    @DeleteMapping("/api/urls/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a short URL")
    void deactivate(@PathVariable String code) { service.deactivate(code); }

    private String baseUrl(HttpServletRequest request) {
        StringBuilder value = new StringBuilder(request.getScheme()).append("://").append(request.getServerName());
        if (!(request.getScheme().equals("http") && request.getServerPort() == 80)
                && !(request.getScheme().equals("https") && request.getServerPort() == 443))
            value.append(':').append(request.getServerPort());
        return value.toString();
    }

    public record CreateShortUrlRequest(
            @NotBlank @Size(max = 2048) String targetUrl,
            @Future Instant expiresAt) { }
    public record ShortUrlView(String code, String targetUrl, String shortUrl, Instant createdAt,
                               Instant expiresAt, boolean active, long redirectCount) {
        static ShortUrlView from(ShortUrl value, String baseUrl) {
            return new ShortUrlView(value.getShortCode(), value.getTargetUrl(),
                    baseUrl + "/" + value.getShortCode(), value.getCreatedAt(), value.getExpiresAt(),
                    value.isActive(), value.getRedirectCount());
        }
    }
}
