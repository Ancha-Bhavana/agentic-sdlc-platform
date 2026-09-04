package bhavana.agenticsdlc.platform.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,80}")
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        chain.doFilter(request, response);
    }
}
