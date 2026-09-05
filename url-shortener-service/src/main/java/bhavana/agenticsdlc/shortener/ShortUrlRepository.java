package bhavana.agenticsdlc.shortener;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {
    Optional<ShortUrl> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    @Modifying
    @Query("delete from ShortUrl u where u.createdAt < :cutoff and (u.active=false or u.expiresAt < :now) "
            + "and not exists (select e.id from RedirectEvent e where e.shortUrlId=u.id)")
    long deleteRetiredBefore(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
