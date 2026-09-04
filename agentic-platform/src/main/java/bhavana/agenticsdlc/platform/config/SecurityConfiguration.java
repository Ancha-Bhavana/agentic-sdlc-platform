package bhavana.agenticsdlc.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean UserDetailsService users(SecurityProperties properties, PasswordEncoder encoder) {
        requirePasswords(properties);
        return new InMemoryUserDetailsManager(
                User.withUsername("operator").password(encoder.encode(properties.operatorPassword())).roles("OPERATOR").build(),
                User.withUsername("approver").password(encoder.encode(properties.approverPassword())).roles("APPROVER").build(),
                User.withUsername("release-approver").password(encoder.encode(properties.releasePassword())).roles("RELEASE_APPROVER").build());
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) ->
                                writeProblem(response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, failure) ->
                                writeProblem(response, 403, "Insufficient role")))
                .httpBasic(Customizer.withDefaults()).build();
    }

    private void requirePasswords(SecurityProperties properties) {
        if (properties.operatorPassword() == null || properties.operatorPassword().isBlank()
                || properties.approverPassword() == null || properties.approverPassword().isBlank()
                || properties.releasePassword() == null || properties.releasePassword().isBlank())
            throw new IllegalStateException("All platform role passwords must be configured");
    }

    private void writeProblem(HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String title = status == 401 ? "Unauthorized" : "Forbidden";
        response.getWriter().write("{\"type\":\"urn:agentic-sdlc:problem:" + status
                + "\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
