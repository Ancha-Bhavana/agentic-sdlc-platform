package bhavana.agenticsdlc.shortener;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redirect_event")
public class RedirectEvent {
    @Id private UUID id;
    @Column(name = "short_url_id", nullable = false) private UUID shortUrlId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    protected RedirectEvent() { }
    RedirectEvent(UUID shortUrlId, Instant occurredAt) {
        this.id = UUID.randomUUID(); this.shortUrlId = shortUrlId; this.occurredAt = occurredAt;
    }
}
