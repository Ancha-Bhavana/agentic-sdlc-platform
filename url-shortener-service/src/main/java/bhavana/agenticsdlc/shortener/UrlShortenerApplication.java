package bhavana.agenticsdlc.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ShortenerProperties.class)
public class UrlShortenerApplication {
    @Bean Clock clock() { return Clock.systemUTC(); }
    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
