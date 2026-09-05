package bhavana.agenticsdlc.shortener;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.*;
import java.util.UUID;

@Service
public class UrlShortenerService {
    private static final int MAX_CODE_ATTEMPTS = 8;
    private final ShortUrlRepository urls;
    private final RedirectEventRepository events;
    private final CodeGenerator codes;
    private final Clock clock;
    private final TargetUrlPolicy targets;
    UrlShortenerService(ShortUrlRepository urls, RedirectEventRepository events, CodeGenerator codes, Clock clock,
                        TargetUrlPolicy targets) {
        this.urls = urls; this.events = events; this.codes = codes; this.clock = clock; this.targets = targets;
    }

    @Transactional
    public ShortUrl create(String targetUrl, Instant expiresAt) {
        URI uri = targets.validate(targetUrl);
        Instant now = clock.instant();
        if (expiresAt != null && !expiresAt.isAfter(now)) throw new IllegalArgumentException("Expiration must be in the future");
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codes.nextCode();
            if (code == null || !code.matches("[A-Za-z0-9]{6,12}") || urls.existsByShortCode(code)) continue;
            try { return urls.saveAndFlush(new ShortUrl(UUID.randomUUID(), code, uri.toASCIIString(), now, expiresAt)); }
            catch (DataIntegrityViolationException collision) { /* retry a concurrent collision */ }
        }
        throw new IllegalStateException("Unable to allocate a unique short code");
    }

    @Transactional
    public URI resolve(String code) {
        ShortUrl url = requireAvailable(code);
        url.recordRedirect();
        events.save(new RedirectEvent(url.getId(), clock.instant()));
        return URI.create(url.getTargetUrl());
    }

    @Transactional(readOnly = true)
    public ShortUrl require(String code) {
        return urls.findByShortCode(code).orElseThrow(() -> new ShortUrlNotFoundException(code));
    }

    @Transactional(readOnly = true)
    public Analytics analytics(String code, LocalDate date) {
        ShortUrl url = require(code);
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Analytics(url.getRedirectCount(), date,
                events.countForPeriod(url.getId(), from, from.plus(Duration.ofDays(1))));
    }

    @Transactional
    public void deactivate(String code) { require(code).deactivate(); }

    private ShortUrl requireAvailable(String code) {
        ShortUrl url = require(code);
        if (!url.availableAt(clock.instant())) throw new ShortUrlNotFoundException(code);
        return url;
    }

    public record Analytics(long totalRedirects, LocalDate date, long redirectsOnDate) { }
}
