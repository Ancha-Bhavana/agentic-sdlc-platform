package bhavana.agenticsdlc.shortener;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
final class RequestRateLimitFilter extends OncePerRequestFilter {
    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    RequestRateLimitFilter(ShortenerProperties properties, Clock clock) {
        limit = properties.rateLimit(); window = properties.rateWindow(); this.clock = clock;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        Instant now = clock.instant();
        Bucket bucket = buckets.compute(request.getRemoteAddr(), (key, current) ->
                current == null || !current.started.plus(window).isAfter(now)
                        ? new Bucket(now, 1) : new Bucket(current.started, current.count + 1));
        if (bucket.count > limit) {
            response.setStatus(429); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(Math.max(1, window.toSeconds())));
            response.getWriter().write("{\"type\":\"urn:url-shortener:rate-limit\",\"title\":\"Too Many Requests\",\"status\":429}");
            return;
        }
        chain.doFilter(request, response);
    }
    private record Bucket(Instant started, int count) {}
}
