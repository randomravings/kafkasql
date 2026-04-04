package kafkasql.linter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kafkasql.runtime.diagnostics.DiagnosticEntry;

/**
 * Loads {@link LintSettings} from TOML configuration files ({@code kafkasql.rules.toml}
 * or {@code *.proj.toml}).
 *
 * <p>Only {@code [lint.*]} sections are read; all other sections are silently ignored.
 * This means the same file can hold both compatibility rules ({@code [compatibility.*]})
 * and lint settings ({@code [lint.*]}) without conflict.
 *
 * <p><b>Section → category mapping</b>:
 * <table>
 *   <tr><th>TOML section</th><th>Rule category</th></tr>
 *   <tr><td>{@code [lint.naming]}</td><td>{@code naming}</td></tr>
 * </table>
 *
 * <p>Keys are the rule ids (the part after the category in the qualified id).
 * Values are severity names or {@code "OFF"} to disable the rule:
 * <pre>
 * [lint.naming]
 * pascal-case-types           = "WARNING"
 * pascal-case-fields          = "OFF"
 * screaming-snake-case-enums  = "ERROR"
 * exact-case-member-reference = "INFO"
 * </pre>
 *
 * <p>Valid severity values: {@code OFF}, {@code INFO}, {@code WARNING}, {@code ERROR},
 * {@code FATAL}.
 *
 * <p>Unknown sections (e.g. {@code [compatibility.*]}, {@code [project]}) and unknown
 * keys are silently ignored, so the file format can grow without breaking older tooling.
 */
public final class LintSettingsLoader {

    /** Prefix that identifies lint sections. */
    private static final String LINT_PREFIX = "lint.";

    private LintSettingsLoader() {}

    /**
     * Load lint settings from a TOML file.
     * Sections other than {@code [lint.*]} are silently skipped.
     *
     * @param path path to the TOML file
     * @return configured {@link LintSettings}
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if an invalid severity value is found
     */
    public static LintSettings load(Path path) throws IOException {
        return parse(Files.readAllLines(path), path.toString());
    }

    /**
     * Generate a TOML snippet for the {@code [lint.*]} sections, listing every
     * built-in rule with its default severity commented out.
     *
     * <p>Suitable for embedding in a full {@code kafkasql.rules.toml} template.
     *
     * @return TOML text covering all known lint rules
     */
    public static String generate() {
        var sb = new StringBuilder();
        sb.append("# ── Lint rules ──────────────────────────────────────────────────────────────\n");
        sb.append("# Override per-rule severity or set \"OFF\" to disable a rule.\n");
        sb.append("# Valid values: OFF  INFO  WARNING  ERROR  FATAL\n");
        sb.append("\n[lint.naming]\n");
        for (LintRule rule : LintEngine.builtInRules()) {
            RuleMetadata m = rule.metadata();
            if (!"naming".equals(m.category())) continue;
            sb.append(String.format("# %-36s= \"%s\"%n",
                m.id() + " ", m.defaultSeverity().name()));
        }
        return sb.toString();
    }

    // ── Private parse core ────────────────────────────────────────────────────

    private static LintSettings parse(List<String> lines, String source) {
        LintSettings settings = LintSettings.defaults();
        String section = null;      // full section name, e.g. "lint.naming"
        String category = null;     // the part after "lint.", e.g. "naming"
        int lineNo = 0;

        for (String raw : lines) {
            lineNo++;
            String line = stripComment(raw).strip();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                section  = parseSection(line, lineNo, source);
                category = section.startsWith(LINT_PREFIX)
                    ? section.substring(LINT_PREFIX.length())
                    : null;
                continue;
            }

            // Only process keys inside [lint.*] sections
            if (category == null) continue;

            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(
                    source + ":" + lineNo + ": expected 'key = \"VALUE\"', got: " + raw.strip());
            }

            String key    = line.substring(0, eq).strip().toLowerCase();
            String valRaw = line.substring(eq + 1).strip();
            String valStr = unquote(valRaw, lineNo, source);

            // Qualified id = "category/key", e.g. "naming/pascal-case-types"
            String qualifiedId = category + "/" + key;

            if (valStr.equalsIgnoreCase("OFF")) {
                settings = settings.disable(qualifiedId);
            } else {
                DiagnosticEntry.Severity severity;
                try {
                    severity = DiagnosticEntry.Severity.valueOf(valStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        source + ":" + lineNo + ": unknown lint severity '" + valStr
                        + "' for rule '" + qualifiedId
                        + "' — expected OFF, INFO, WARNING, ERROR, or FATAL");
                }
                settings = settings.with(qualifiedId, severity);
            }
        }

        return settings;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String parseSection(String line, int lineNo, String source) {
        if (!line.endsWith("]")) {
            throw new IllegalArgumentException(
                source + ":" + lineNo + ": malformed section header: " + line);
        }
        return line.substring(1, line.length() - 1).strip();
    }

    private static String unquote(String val, int lineNo, String source) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        throw new IllegalArgumentException(
            source + ":" + lineNo + ": value must be a quoted string, got: " + val);
    }

    private static String stripComment(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == '#' && !inQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
