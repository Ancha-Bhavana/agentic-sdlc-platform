package bhavana.agenticsdlc.shortener;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {
    Optional<ShortUrl> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
}
