package bhavana.agenticsdlc.platform.agent;
public final class AgentSchemas {
    private AgentSchemas() {}
    public static final String RESULT = """
      {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","additionalProperties":false,
       "required":["reasoningSummary","assumptions","decisions","risks","evidence","artifact"],
       "properties":{"reasoningSummary":{"type":"string","minLength":1,"maxLength":4000},
       "assumptions":{"type":"array","maxItems":50,"items":{"type":"string"}},"decisions":{"type":"array","maxItems":50,"items":{"type":"string"}},
       "risks":{"type":"array","maxItems":50,"items":{"type":"string"}},"evidence":{"type":"array","maxItems":100,"items":{"type":"string"}},
       "artifact":{"type":"object","additionalProperties":false,"required":["kind","schemaVersion","content"],"properties":{"kind":{"type":"string","minLength":1},"schemaVersion":{"type":"string","const":"1.0"},"content":{"type":"object"}}}}}
      """;
}
