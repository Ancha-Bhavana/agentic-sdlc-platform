package bhavana.agenticsdlc.shortener;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shortener;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate"})
@Transactional
class UrlShortenerServiceTest {
    @Autowired UrlShortenerService service;
    @MockitoBean CodeGenerator codes;

    @BeforeEach void codes() { when(codes.nextCode()).thenReturn("Abc23456"); }

    @Test void createsResolvesAndCountsUtcDailyAnalytics() {
        ShortUrl created = service.create("https://example.com/a?b=c", null);
        assertThat(created.getShortCode()).isEqualTo("Abc23456");
        assertThat(service.resolve(created.getShortCode())).isEqualTo(URI.create("https://example.com/a?b=c"));
        assertThat(service.analytics(created.getShortCode(), LocalDate.now(ZoneOffset.UTC)))
                .extracting(UrlShortenerService.Analytics::totalRedirects,
                        UrlShortenerService.Analytics::redirectsOnDate)
                .containsExactly(1L, 1L);
    }

    @Test void rejectsUnsafeTargetsAndExpiredLinks() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create("file:///etc/passwd", null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.create("https://example.com", Instant.EPOCH));
    }

    @Test void retriesCodeCollisionAndDeactivationStopsRedirects() {
        when(codes.nextCode()).thenReturn("Abc23456", "Def23456");
        service.create("https://one.example", null);
        ShortUrl second = service.create("https://two.example", null);
        assertThat(second.getShortCode()).isEqualTo("Def23456");
        service.deactivate(second.getShortCode());
        assertThatThrownBy(() -> service.resolve(second.getShortCode()))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
