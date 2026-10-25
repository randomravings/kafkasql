package kafkasql.lsp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaSqlProjectTest {

    @AfterEach
    void clearCache() {
        KafkaSqlProject.clearCache();
    }

    @Test
    void expectedContext_ignoresModelCategoryFolders() {
        Path repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
        assertTrue(repoRoot != null, "Expected lsp module to have a parent repo directory");

        Path typesFile = repoRoot.resolve("examples/project/model/types/com/example/Amount.kafka");
        Path streamsFile = repoRoot.resolve("examples/project/model/streams/orders/Orders.kafka");
        Path groupsFile = repoRoot.resolve("examples/project/model/groups/customers/read-test.kafka");

        Optional<KafkaSqlProject> projectOpt = KafkaSqlProject.findFor(typesFile);
        assertTrue(projectOpt.isPresent(), "Expected to find KafkaSQL project for examples/project files");
        KafkaSqlProject project = projectOpt.get();

        assertEquals(Optional.of("com.example"), project.expectedContext(typesFile));
        assertEquals(Optional.of("orders"), project.expectedContext(streamsFile));
        assertEquals(Optional.of("customers"), project.expectedContext(groupsFile));
    }

    @Test
    void expectedContext_contextDeclarationFileUsesParentContext() {
        Path repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
        assertTrue(repoRoot != null, "Expected lsp module to have a parent repo directory");

        Path contextFile = repoRoot.resolve("examples/project/model/contexts/com/example.kafka");
        Optional<KafkaSqlProject> projectOpt = KafkaSqlProject.findFor(contextFile);
        assertTrue(projectOpt.isPresent(), "Expected to find KafkaSQL project for context declaration file");

        assertEquals(Optional.of("com"), projectOpt.get().expectedContext(contextFile));
    }
}
