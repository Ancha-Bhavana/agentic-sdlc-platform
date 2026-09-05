package bhavana.agenticsdlc.shortener;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.Set;

@ConfigurationProperties("shortener")
public record ShortenerProperties(String region, int rateLimit, Duration rateWindow,
                                  Set<String> blockedHosts, Duration eventRetention,
                                  Duration urlRetention, Duration cleanupInterval) {
    public ShortenerProperties {
        region = region == null || region.isBlank() ? "us" : region.toLowerCase();
        if (!region.matches("[a-z0-9]{2,4}")) throw new IllegalArgumentException("Region must be 2-4 lowercase letters or digits");
        rateLimit = rateLimit < 1 ? 120 : rateLimit;
        rateWindow = rateWindow == null ? Duration.ofMinutes(1) : rateWindow;
        blockedHosts = blockedHosts == null ? Set.of() : blockedHosts.stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
        eventRetention = eventRetention == null ? Duration.ofDays(90) : eventRetention;
        urlRetention = urlRetention == null ? Duration.ofDays(365) : urlRetention;
        cleanupInterval = cleanupInterval == null ? Duration.ofHours(24) : cleanupInterval;
    }
}
