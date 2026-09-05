package bhavana.agenticsdlc.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:shortener-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate"})
class UrlShortenerApiTest {
    @LocalServerPort int port;
    @MockitoBean CodeGenerator codes;

    @Test void createRedirectInspectAndAnalyticsWorkOverHttp() {
        when(codes.nextCode()).thenReturn("Api23456");
        RestClient client = RestClient.create("http://localhost:" + port);
        Map<?, ?> created = client.post().uri("/api/urls").body(Map.of("targetUrl", "https://example.com/docs"))
                .retrieve().body(Map.class);
        assertThat(created.get("code")).isEqualTo("Api23456");
        var redirect = client.get().uri("/Api23456").retrieve().toBodilessEntity();
        assertThat(redirect.getStatusCode().value()).isEqualTo(302);
        assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/docs");
        Map<?, ?> analytics = client.get().uri("/api/urls/Api23456/analytics").retrieve().body(Map.class);
        assertThat(analytics.get("totalRedirects")).isEqualTo(1);
    }
}
