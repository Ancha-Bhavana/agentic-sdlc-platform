package bhavana.agenticsdlc.shortener;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.*;

interface RedirectEventRepository extends JpaRepository<RedirectEvent, UUID> {
    @Query("select count(e) from RedirectEvent e where e.shortUrlId = :shortUrlId and e.occurredAt >= :from and e.occurredAt < :to")
    long countForPeriod(UUID shortUrlId, Instant from, Instant to);
}
