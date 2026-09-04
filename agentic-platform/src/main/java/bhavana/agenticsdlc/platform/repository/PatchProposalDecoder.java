package bhavana.agenticsdlc.platform.repository;

import bhavana.agenticsdlc.platform.agent.AgentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict tool-boundary decoding; generic agent artifacts never imply executable patches. */
public final class PatchProposalDecoder {
    private final ObjectMapper mapper;
    public PatchProposalDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    public List<FileOperation> decode(AgentResult result) {
        var operations = mapper.valueToTree(result.artifact().content()).get("operations");
        if (operations == null || !operations.isArray() || operations.size() > 100)
            throw new RepositorySecurityException("Patch requires an operations array of at most 100 entries");
        List<FileOperation> decoded = new ArrayList<>();
        for (var operation : operations) {
            if (!operation.isObject()) throw new RepositorySecurityException("Operation must be an object");
            operation.fieldNames().forEachRemaining(key -> {
                if (!Set.of("type", "path", "expectedHash", "content").contains(key))
                    throw new RepositorySecurityException("Unknown patch field");
            });
            if (!operation.path("type").isTextual() || !operation.path("path").isTextual()
                    || operation.path("path").asText().isBlank())
                throw new RepositorySecurityException("Operation type and path required");
            FileOperation.Type type;
            try { type = FileOperation.Type.valueOf(operation.get("type").asText()); }
            catch (IllegalArgumentException e) { throw new RepositorySecurityException("Unsupported operation"); }
            if (type != FileOperation.Type.DELETE && !operation.path("content").isTextual())
                throw new RepositorySecurityException("Text content required");
            if (type == FileOperation.Type.DELETE && operation.hasNonNull("content"))
                throw new RepositorySecurityException("Delete cannot contain content");
            if (type != FileOperation.Type.CREATE && (!operation.path("expectedHash").isTextual()
                    || !operation.get("expectedHash").asText().matches("[0-9a-f]{64}")))
                throw new RepositorySecurityException("Expected SHA-256 required");
            decoded.add(new FileOperation(type, operation.get("path").asText(),
                    operation.path("expectedHash").isTextual() ? operation.get("expectedHash").asText() : null,
                    type == FileOperation.Type.DELETE ? null
                            : operation.get("content").asText().getBytes(StandardCharsets.UTF_8)));
        }
        return List.copyOf(decoded);
    }
}
