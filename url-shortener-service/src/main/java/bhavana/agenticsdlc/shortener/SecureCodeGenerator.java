package bhavana.agenticsdlc.shortener;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
final class SecureCodeGenerator implements CodeGenerator {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final String region;
    SecureCodeGenerator(ShortenerProperties properties) { this.region = properties.region(); }
    public String nextCode() {
        char[] value = new char[8];
        for (int i = 0; i < value.length; i++) value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return region + new String(value);
    }
}
