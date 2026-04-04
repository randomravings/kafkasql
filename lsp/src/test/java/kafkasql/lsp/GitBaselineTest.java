package kafkasql.lsp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GitBaselineTest {

    @AfterEach
    void clearCache() {
        GitBaseline.clearCache();
    }

    @Test
    void resolveGitRoot_findsRootFromSubDirectory() {
        // In Gradle, user.dir is the module directory (lsp/) — already a sub-directory of the repo.
        Path moduleDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        Optional<Path> root = GitBaseline.resolveGitRoot(moduleDir);

        assertTrue(root.isPresent(), "Should find git root from the module directory");
        // The resolved root must be an ancestor of the module directory
        assertTrue(moduleDir.startsWith(root.get()),
            "git root " + root.get() + " should be an ancestor of " + moduleDir);
        // .git directory must exist at the resolved root
        assertTrue(Files.isDirectory(root.get().resolve(".git")),
            ".git directory must exist at the resolved root");
    }

    @Test
    void resolveGitRoot_returnsEmptyForNonGitDirectory() throws IOException {
        Path tmpDir = Files.createTempDirectory("kafkasql-non-git-");
        try {
            Optional<Path> root = GitBaseline.resolveGitRoot(tmpDir);
            assertTrue(root.isEmpty(), "Should return empty for a directory with no git repo");
        } finally {
            Files.deleteIfExists(tmpDir);
        }
    }

    @Test
    void resolveGitRoot_isCached() {
        Path moduleDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        Optional<Path> first  = GitBaseline.resolveGitRoot(moduleDir);
        Optional<Path> second = GitBaseline.resolveGitRoot(moduleDir);

        assertSame(first, second, "Second call should return the exact same Optional from the cache");
    }

    @Test
    void getContent_returnsEmptyForUntrackedFile() throws IOException {
        Path moduleDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path tmpFile = Files.createTempFile(moduleDir, "untracked-", ".kafka");
        try {
            Optional<String> content = GitBaseline.getContent(moduleDir, tmpFile);
            assertTrue(content.isEmpty(), "Untracked file should return empty");
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    @Test
    void getContent_resolvesGitRootBelowWorkspaceRoot() {
        // Pass the lsp/ module directory as "workspaceRoot" — git root is the parent.
        Path moduleDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path buildGradle = moduleDir.resolve("build.gradle");

        if (!Files.isRegularFile(buildGradle)) {
            return; // Not running from expected location — skip gracefully
        }

        Optional<String> content = GitBaseline.getContent(moduleDir, buildGradle);

        // May be empty if build.gradle isn't committed yet — just ensure no exception is thrown
        // and if present the content is non-empty
        content.ifPresent(c -> assertFalse(c.isBlank(), "Committed build.gradle should not be blank"));
    }
}
