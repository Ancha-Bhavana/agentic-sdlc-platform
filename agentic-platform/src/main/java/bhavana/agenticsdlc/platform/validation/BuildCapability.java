package bhavana.agenticsdlc.platform.validation;

import java.util.List;

public enum BuildCapability {
    MAVEN_TEST(List.of("-B", "-o", "test")),
    MAVEN_VERIFY(List.of("-B", "-o", "verify"));

    private final List<String> arguments;
    BuildCapability(List<String> arguments) { this.arguments = arguments; }
    public List<String> arguments() { return arguments; }
}
