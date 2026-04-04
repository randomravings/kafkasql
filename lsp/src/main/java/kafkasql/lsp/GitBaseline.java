package kafkasql.lsp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retrieves the contents of a file from the current git HEAD commit.
 *
 * <p>This is used by the LSP to obtain the "before" version of a file so that
 * the diff/compatibility pipeline can compare it against the current editor
 * buffer and report breaking or warning-level changes as diagnostics.
 *
 * <p>Returns {@link Optional#empty()} gracefully in any of these cases:
 * <ul>
 *   <li>The file is not tracked by git (new file).</li>
 *   <li>The workspace is not a git repository.</li>
 *   <li>git is not installed or not on PATH.</li>
 *   <li>Any other process-level failure.</li>
 * </ul>
 */
public final class GitBaseline {

    private GitBaseline() {}

    /**
     * Cache: workspaceRoot → git repo root (or empty sentinel).
     * Avoids re-running `git rev-parse` on every keystroke.
     */
    private static final ConcurrentHashMap<Path, Optional<Path>> gitRootCache =
        new ConcurrentHashMap<>();

    /** Sentinel stored in the cache when a directory is not inside a git repo. */
    private static final Optional<Path> NOT_A_REPO = Optional.empty();

    /**
     * Retrieve the HEAD content of {@code absoluteFilePath} from the git repository
     * that contains it.
     *
     * <p>The git repository root is resolved via {@code git rev-parse --show-toplevel}
     * so it works even when {@code workspaceRoot} is a sub-directory of the repo or
     * when the repo root is a parent of {@code workspaceRoot}.
     *
     * @param workspaceRoot    any directory within (or equal to) the git repository
     * @param absoluteFilePath absolute path to the file whose baseline is needed
     * @return file content at HEAD, or empty if unavailable
     */
    public static Optional<String> getContent(Path workspaceRoot, Path absoluteFilePath) {
        Optional<Path> gitRoot = resolveGitRoot(workspaceRoot);
        if (gitRoot.isEmpty()) {
            return Optional.empty();
        }

        try {
            Path root = gitRoot.get();
            Path rel = root.relativize(absoluteFilePath.normalize());
            String gitPath = rel.toString().replace('\\', '/');

            ProcessBuilder pb = new ProcessBuilder(
                "git", "-C", root.toString(),
                "show", "HEAD:" + gitPath);
            pb.redirectErrorStream(false);

            Process process = pb.start();
            byte[] output = process.getInputStream().readAllBytes();
            int exit = process.waitFor();

            return exit == 0 ? Optional.of(new String(output, StandardCharsets.UTF_8))
                             : Optional.empty();

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve the git repository root for a given starting directory.
     * Result is cached per {@code startDir} so only one process is spawned per workspace.
     */
    static Optional<Path> resolveGitRoot(Path startDir) {
        return gitRootCache.computeIfAbsent(startDir, dir -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "git", "-C", dir.toAbsolutePath().toString(),
                    "rev-parse", "--show-toplevel");
                pb.redirectErrorStream(false);

                Process process = pb.start();
                byte[] output = process.getInputStream().readAllBytes();
                int exit = process.waitFor();

                if (exit != 0) {
                    return NOT_A_REPO;
                }

                String toplevel = new String(output, StandardCharsets.UTF_8).strip();
                Path gitRoot = Path.of(toplevel).toAbsolutePath().normalize();
                System.err.println("[kafkasql-lsp] git root for " + dir + " → " + gitRoot);
                return Optional.of(gitRoot);

            } catch (Exception e) {
                return NOT_A_REPO;
            }
        });
    }

    /** Clear the git-root cache (useful for testing). */
    static void clearCache() {
        gitRootCache.clear();
    }
}
