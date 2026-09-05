package bhavana.agenticsdlc.shortener;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "short_url")
public class ShortUrl {
    @Id private UUID id;
    @Column(name = "short_code", nullable = false, unique = true, length = 12) private String shortCode;
    @Column(name = "target_url", nullable = false, length = 2048) private String targetUrl;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(nullable = false) private boolean active;
    @Column(name = "redirect_count", nullable = false) private long redirectCount;
    @Version @Column(name = "entity_version", nullable = false) private long version;

    protected ShortUrl() { }
    ShortUrl(UUID id, String shortCode, String targetUrl, Instant createdAt, Instant expiresAt) {
        this.id = id; this.shortCode = shortCode; this.targetUrl = targetUrl;
        this.createdAt = createdAt; this.expiresAt = expiresAt; this.active = true;
    }
    public UUID getId() { return id; }
    public String getShortCode() { return shortCode; }
    public String getTargetUrl() { return targetUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public long getRedirectCount() { return redirectCount; }
    boolean availableAt(Instant now) { return active && (expiresAt == null || expiresAt.isAfter(now)); }
    void recordRedirect() { redirectCount++; }
    void deactivate() { active = false; }
}
