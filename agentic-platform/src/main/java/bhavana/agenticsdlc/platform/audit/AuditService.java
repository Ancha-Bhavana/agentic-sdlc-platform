package bhavana.agenticsdlc.platform.audit;

import bhavana.agenticsdlc.platform.repository.FileHashService;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuditService {
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(api[_-]?key|authorization|password|secret)(\\s*[:=]\\s*)([^,;\\s]+)");
    private final AuditEventRepository events;
    private final Clock clock;
    private final FileHashService hashes = new FileHashService();
    public AuditService(AuditEventRepository events, Clock clock) { this.events = events; this.clock = clock; }

    public AuditEventEntity record(UUID workflowId, Integer revision, String correlationId,
                                   String eventType, ActorIdentity actor, String payload) {
        String safePayload = redact(payload == null ? "" : payload);
        String hash = hashes.sha256((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
        return events.save(new AuditEventEntity(workflowId, revision, bounded(correlationId, 80),
                bounded(eventType, 80), bounded(actor.name(), 200), bounded(actor.role(), 80), hash,
                bounded(safePayload, 2000), clock.instant()));
    }

    String redact(String value) { return SENSITIVE.matcher(value).replaceAll("$1$2[REDACTED]"); }
    private String bounded(String value, int maximum) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Audit fields must not be blank");
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public record ActorIdentity(String name, String role) { }
}
