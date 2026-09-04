package bhavana.agenticsdlc.platform.repository;

public record FileOperation(Type type, String path, String expectedHash, byte[] content) {
    public FileOperation { content = content == null ? null : content.clone(); }
    @Override public byte[] content() { return content == null ? null : content.clone(); }
    public enum Type { CREATE, UPDATE, DELETE }
}
