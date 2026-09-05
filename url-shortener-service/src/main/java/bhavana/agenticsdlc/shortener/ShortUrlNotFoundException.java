package bhavana.agenticsdlc.shortener;
public final class ShortUrlNotFoundException extends RuntimeException {
    ShortUrlNotFoundException(String code) { super("Short URL not found, inactive, or expired: " + code); }
}
