package bhavana.agenticsdlc.shortener;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class RetentionService {
    private final RedirectEventRepository events;
    private final ShortUrlRepository urls;
    private final ShortenerProperties properties;
    private final Clock clock;
    public RetentionService(RedirectEventRepository events, ShortUrlRepository urls,
                     ShortenerProperties properties, Clock clock) {
        this.events = events; this.urls = urls; this.properties = properties; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${shortener.cleanup-interval:24h}")
    @Transactional
    public CleanupResult clean() {
        var now = clock.instant();
        long removedEvents = events.deleteByOccurredAtBefore(now.minus(properties.eventRetention()));
        long removedUrls = urls.deleteRetiredBefore(now.minus(properties.urlRetention()), now);
        return new CleanupResult(removedEvents, removedUrls);
    }
    record CleanupResult(long redirectEvents, long urls) {}
}
