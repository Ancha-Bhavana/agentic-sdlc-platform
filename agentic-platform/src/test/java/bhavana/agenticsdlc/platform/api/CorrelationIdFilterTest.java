package bhavana.agenticsdlc.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    @Test void preservesValidCorrelationIdAndRejectsHeaderInjection() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.addHeader(CorrelationIdFilter.HEADER, "review-123");
        MockHttpServletResponse validResponse = new MockHttpServletResponse();
        filter.doFilter(valid, validResponse, new MockFilterChain());
        assertThat(validResponse.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("review-123");

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader(CorrelationIdFilter.HEADER, "bad value");
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(invalid, invalidResponse, new MockFilterChain());
        assertThat(invalidResponse.getHeader(CorrelationIdFilter.HEADER))
                .matches("[0-9a-f-]{36}").isNotEqualTo("bad value");
    }
}
