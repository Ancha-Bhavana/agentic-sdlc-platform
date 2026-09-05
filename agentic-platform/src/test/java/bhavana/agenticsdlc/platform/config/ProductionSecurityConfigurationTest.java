package bhavana.agenticsdlc.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ProductionSecurityConfigurationTest {
    @Test void mapsOidcRolesClaimToPlatformAuthorities() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"), java.util.Map.of("sub", "reviewer", "roles", List.of("operator", "ROLE_APPROVER")));
        var authentication = new SecurityConfiguration().jwtRoles().convert(jwt);
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_OPERATOR", "ROLE_APPROVER");
        assertThat(authentication.getName()).isEqualTo("reviewer");
    }
}
