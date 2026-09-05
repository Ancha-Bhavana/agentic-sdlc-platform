package bhavana.agenticsdlc.shortener;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Locale;

@Component
final class TargetUrlPolicy {
    private final ShortenerProperties properties;
    TargetUrlPolicy(ShortenerProperties properties) { this.properties = properties; }

    URI validate(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || host.isBlank() || uri.getUserInfo() != null || uri.getFragment() != null
                    || host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                    || properties.blockedHosts().contains(host) || privateLiteral(host))
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Target URL violates the public HTTP(S) destination policy");
        }
    }

    private boolean privateLiteral(String host) {
        if (host.equals("::1") || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")) return true;
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return false;
        String[] octets = host.split("\\.");
        int a = Integer.parseInt(octets[0]), b = Integer.parseInt(octets[1]);
        return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254)
                || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || a >= 224;
    }
}
