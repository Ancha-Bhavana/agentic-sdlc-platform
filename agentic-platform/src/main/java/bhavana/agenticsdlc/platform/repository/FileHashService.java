package bhavana.agenticsdlc.platform.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FileHashService {
    public String sha256(Path file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
        catch (IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("Cannot hash " + file, e); }
    }

    public String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
