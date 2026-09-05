package bhavana.agenticsdlc.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionControlsTest {
    private ShortenerProperties properties(int limit) {
        return new ShortenerProperties("eu", limit, Duration.ofMinutes(1), Set.of("blocked.example"),
                Duration.ofDays(30), Duration.ofDays(90), Duration.ofHours(1));
    }

    @Test void regionPrefixProvidesIndependentAllocationNamespace() {
        String code = new SecureCodeGenerator(properties(10)).nextCode();
        assertThat(code).startsWith("eu").matches("[A-Za-z0-9]{10}");
    }

    @Test void targetPolicyRejectsConfiguredAndPrivateDestinations() {
        TargetUrlPolicy policy = new TargetUrlPolicy(properties(10));
        assertThat(policy.validate("https://public.example/path").getHost()).isEqualTo("public.example");
        assertThatThrownBy(() -> policy.validate("https://blocked.example/path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("http://192.168.1.1/path")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rateLimiterReturnsRfcProblemAfterConfiguredLimit() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(properties(1), Clock.systemUTC());
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/abc");
        first.setRemoteAddr("203.0.113.10");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/abc");
        second.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getHeader("Retry-After")).isNotBlank();
    }

    @Test void retentionDeletesEventsBeforeUrls() {
        RedirectEventRepository events = mock(RedirectEventRepository.class);
        ShortUrlRepository urls = mock(ShortUrlRepository.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(events.deleteByOccurredAtBefore(any())).thenReturn(4L);
        when(urls.deleteRetiredBefore(any(), eq(now))).thenReturn(2L);
        var result = new RetentionService(events, urls, properties(10), Clock.fixed(now, ZoneOffset.UTC)).clean();
        assertThat(result.redirectEvents()).isEqualTo(4);
        assertThat(result.urls()).isEqualTo(2);
        var order = inOrder(events, urls);
        order.verify(events).deleteByOccurredAtBefore(now.minus(Duration.ofDays(30)));
        order.verify(urls).deleteRetiredBefore(now.minus(Duration.ofDays(90)), now);
    }
}
