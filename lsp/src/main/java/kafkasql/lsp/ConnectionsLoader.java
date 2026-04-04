package kafkasql.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code connections.toml} file into a list of {@link ConnectionConfig}.
 *
 * <p>File format (TOML subset):
 * <pre>
 * [connection.prod]
 * bootstrap = "broker1:9092,broker2:9092"
 * topic     = "kafkasql.schema-events"
 *
 * [connection.staging]
 * bootstrap = "staging:9092"
 * topic     = "kafkasql.schema-events"
 * </pre>
 *
 * <p>Only {@code [connection.*]} sections are processed; all other sections are ignored.
 */
public final class ConnectionsLoader {

    private ConnectionsLoader() {}

    /** Filename convention: lives alongside the {@code .proj.toml} file. */
    public static final String FILENAME = "connections.toml";

    /**
     * Loads all connections from the {@code connections.toml} file in the given directory.
     * Returns an empty list if the file does not exist or contains no valid connections.
     */
    public static List<ConnectionConfig> load(Path projectDir) {
        Path file = projectDir.resolve(FILENAME);
        if (!Files.exists(file)) return List.of();
        try {
            return parse(file);
        } catch (IOException e) {
            System.err.println("[kafkasql-lsp] Failed to load " + file + ": " + e.getMessage());
            return List.of();
        }
    }

    static List<ConnectionConfig> parse(Path file) throws IOException {
        List<ConnectionConfig> result = new ArrayList<>();
        String currentConnection = null;
        String bootstrap = null;
        String topic = null;

        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("[")) {
                // Flush previous connection before switching section
                if (currentConnection != null && bootstrap != null && topic != null) {
                    result.add(new ConnectionConfig(currentConnection, bootstrap, topic));
                }
                currentConnection = null;
                bootstrap = null;
                topic = null;

                String section = line.replaceAll("^\\[", "").replaceAll("\\].*$", "").trim();
                if (section.startsWith("connection.")) {
                    currentConnection = section.substring("connection.".length()).trim();
                }
                continue;
            }

            if (currentConnection == null) continue;

            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key   = line.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
            String value = line.substring(eq + 1).trim();
            // Strip surrounding quotes and trailing inline comments
            if (value.startsWith("\"")) {
                int close = value.indexOf('"', 1);
                value = close >= 0 ? value.substring(1, close) : value.substring(1);
            } else {
                int ci = value.indexOf('#');
                if (ci >= 0) value = value.substring(0, ci).trim();
            }

            switch (key) {
                case "bootstrap" -> bootstrap = value;
                case "topic"     -> topic     = value;
            }
        }

        // Flush last connection
        if (currentConnection != null && bootstrap != null && topic != null) {
            result.add(new ConnectionConfig(currentConnection, bootstrap, topic));
        }

        return result;
    }
}
