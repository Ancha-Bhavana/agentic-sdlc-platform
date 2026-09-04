package bhavana.agenticsdlc.platform.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class RepositoryContextSelectorTest {
    @TempDir Path root;

    @Test void selectsRelevantSourceButExcludesCredentialsAndBuildOutput() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("src/Shortener.java"), "class Shortener { String analytics; }");
        Files.writeString(root.resolve("src/Secret.java"), "String api_key = \"credential\";");
        Files.writeString(root.resolve("target/Generated.java"), "class Generated { String analytics; }");
        Files.writeString(root.resolve("pom.xml"), "<project/>");

        var selected = new RepositoryContextSelector(10, 10_000).select(root, Set.of("analytics"));

        assertThat(selected).containsKeys("src/Shortener.java", "pom.xml")
                .doesNotContainKeys("src/Secret.java", "target/Generated.java");
    }
}
