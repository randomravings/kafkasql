package kafkasql.lsp;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a KafkaSQL project rooted at a {@code .proj.toml} file.
 *
 * <p>Project file format (TOML subset). Project-specific settings live in a
 * {@code [project]} section; all other sections (e.g. {@code [compatibility.*]},
 * {@code [lint.*]}) are also read by {@link kafkasql.lang.compare.RuleSetLoader}:
 * <pre>
 * [project]
 * name  = myproject
 * kafka = kafka        # folder containing all .kafka source files (default: kafka)
 *
 * [compatibility.struct.field]
 * type_changed = "WARNING"
 * </pre>
 *
 * <p>Convention: a {@code .kafka} file at {@code <kafkaRoot>/a/b/File.kafka} is expected to
 * declare {@code USE CONTEXT a.b}.  The IDE warns when the declared context and folder path
 * are misaligned (like Java's package/folder enforcement).
 */
public final class KafkaSqlProject {

    private static final String EXTENSION = ".proj.toml";

    /** Cache: starting directory → nearest project (empty = no project found). */
    private static final Map<Path, Optional<KafkaSqlProject>> cache = new ConcurrentHashMap<>();

    private final Path   projectRoot; // absolute dir containing the .proj.toml file
    private final Path   projectFile; // absolute path to the .proj.toml file itself
    private final String name;
    private final Path   kafkaRoot;   // absolute path to the kafka source folder

    private KafkaSqlProject(Path projectRoot, Path projectFile, String name, String kafkaRelative) {
        this.projectRoot = projectRoot;
        this.projectFile = projectFile;
        this.name        = name;
        this.kafkaRoot   = projectRoot.resolve(kafkaRelative).normalize();
    }

    public Path   projectRoot() { return projectRoot; }
    public Path   projectFile() { return projectFile; }
    public String name()        { return name; }
    public Path   kafkaRoot()   { return kafkaRoot; }

    // ─────────────────────────────────────────────────────────────────────────
    // Discovery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walk up from {@code startPath} (file or directory) to find the nearest
     * {@code .proj.toml} file.  Result is cached per starting directory.
     */
    public static Optional<KafkaSqlProject> findFor(Path startPath) {
        Path dir = Files.isDirectory(startPath)
            ? startPath.toAbsolutePath().normalize()
            : startPath.toAbsolutePath().normalize().getParent();
        if (dir == null) return Optional.empty();
        return cache.computeIfAbsent(dir, KafkaSqlProject::search);
    }

    private static Optional<KafkaSqlProject> search(Path dir) {
        Path cur = dir;
        while (cur != null) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cur, "*" + EXTENSION)) {
                for (Path file : stream) {
                    try {
                        return Optional.of(parse(file));
                    } catch (IOException e) {
                        System.err.println("[kafkasql-lsp] Failed to parse project file "
                            + file + ": " + e.getMessage());
                    }
                }
            } catch (IOException ignored) {}
            Path parent = cur.getParent();
            if (parent == null || parent.equals(cur)) break;
            cur = parent;
        }
        return Optional.empty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Convention: expected context from folder path
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given an absolute path to a {@code .kafka} file inside this project's kafka root,
     * returns the context name that the file is <em>expected</em> to declare, derived from
     * its folder path relative to the kafka root.
     *
     * <p>Examples (kafka root = {@code project/kafka}):
     * <ul>
     *   <li>{@code project/kafka/orders/Order.kafka}          → {@code orders}</li>
     *   <li>{@code project/kafka/com/example/Order.kafka}     → {@code com.example}</li>
     *   <li>{@code project/kafka/Order.kafka}                 → empty (root level, no constraint)</li>
     *   <li>{@code project/other/Order.kafka}                 → empty (outside kafka root)</li>
     * </ul>
     */
    /**
     * Returns {@code true} when the given file lives <em>outside</em> this project's
     * kafka/model root — i.e. it is a misc / interactive script.  In that mode the
     * editor grays out INCLUDE statements and symbol resolution is deferred to the
     * live Kafka cluster rather than the local source tree.
     */
    public boolean isInteractiveFile(Path absoluteFilePath) {
        Path file = absoluteFilePath.toAbsolutePath().normalize();
        return !file.startsWith(kafkaRoot);
    }

    public Optional<String> expectedContext(Path absoluteFilePath) {
        Path file = absoluteFilePath.toAbsolutePath().normalize();
        if (!file.startsWith(kafkaRoot)) return Optional.empty();

        Path relative = kafkaRoot.relativize(file);
        int depth = relative.getNameCount();
        if (depth < 2) return Optional.empty(); // directly in kafka root — no folder context

        // All segments except the last one (the filename) form the context path
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < depth - 1; i++) {
            segments.add(relative.getName(i).toString());
        }
        return Optional.of(String.join(".", segments));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    private static KafkaSqlProject parse(Path file) throws IOException {
        Path projectRoot = file.getParent().toAbsolutePath().normalize();
        Path projectFile = file.toAbsolutePath().normalize();
        String name = null;
        String kafkaDir = "kafka";
        boolean inProjectSection = false;
        boolean hasProjectSection = false;

        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            // Section header
            if (trimmed.startsWith("[")) {
                String section = trimmed.replaceAll("^\\[", "").replaceAll("\\].*$", "").trim();
                inProjectSection = "project".equals(section);
                if (inProjectSection) hasProjectSection = true;
                continue;
            }
            if (!inProjectSection) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            String key   = trimmed.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
            String value = trimmed.substring(eq + 1).trim();
            // Strip quotes then inline comments
            if (value.startsWith("\"")) {
                int close = value.indexOf('"', 1);
                value = close >= 0 ? value.substring(1, close) : value.substring(1);
            } else {
                int commentIdx = value.indexOf('#');
                if (commentIdx >= 0) value = value.substring(0, commentIdx).trim();
            }
            switch (key) {
                case "name"  -> name     = value;
                case "kafka" -> kafkaDir = value;
            }
        }
        if (!hasProjectSection)
            throw new IOException("no [project] section in " + file.getFileName());
        if (name == null || name.isBlank())
            throw new IOException("no 'name' key in [project] section of " + file.getFileName());
        return new KafkaSqlProject(projectRoot, projectFile, name, kafkaDir);
    }

    /** Clear the discovery cache (used in tests and workspace reload). */
    public static void clearCache() {
        cache.clear();
    }

    @Override
    public String toString() {
        return "KafkaSqlProject{name='" + name + "', root=" + projectRoot + ", kafka=" + kafkaRoot + "}";
    }
}
