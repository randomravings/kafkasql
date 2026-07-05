package kafkasql.lang.compare;

import java.io.IOException;
import kafkasql.linter.LintSettingsLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and generates {@link RuleSet} TOML configuration files ({@code kafkasql.rules.toml}).
 *
 * <p><b>Loading</b>: reads a TOML file and applies any present entries as overrides
 * on top of {@link RuleSet#defaults()}.  Only the keys listed in the file are
 * overridden; all absent keys keep their built-in default.  Unknown top-level sections
 * (e.g. {@code [lint]}) are silently accepted so the file format can grow without
 * breaking older tooling.
 *
 * <p><b>Generating</b>: serialises a {@link RuleSet} instance (typically
 * {@link RuleSet#defaults()}) to TOML so users can see every available key
 * and edit the severities they want to change.
 *
 * <p>Supported TOML subset:
 * <ul>
 *   <li>Section headers: {@code [section]} or {@code [dotted.section]}</li>
 *   <li>String values: {@code key = "VALUE"}</li>
 *   <li>Comments: {@code # …} (line or inline)</li>
 *   <li>Blank lines are ignored</li>
 * </ul>
 *
 * <p>Example ({@code kafkasql.rules.toml}):
 * <pre>
 * [compatibility.struct.field]
 * type_changed = "WARNING"   # relax for internal schemas
 *
 * [compatibility.enum]
 * symbol_added = "SAFE"
 * </pre>
 *
 * <p>The section-to-prefix mapping is:
 * <table>
 *   <tr><th>Section</th><th>RuleKey prefix</th></tr>
 *   <tr><td>{@code [compatibility.statement]}</td><td>(none)</td></tr>
 *   <tr><td>{@code [compatibility.struct.field]}</td><td>{@code STRUCT_FIELD}</td></tr>
 *   <tr><td>{@code [compatibility.enum]}</td><td>{@code ENUM}</td></tr>
 *   <tr><td>{@code [compatibility.union]}</td><td>{@code UNION}</td></tr>
 *   <tr><td>{@code [compatibility.stream]}</td><td>{@code STREAM}</td></tr>
 *   <tr><td>{@code [compatibility.scalar]}</td><td>{@code SCALAR}</td></tr>
 *   <tr><td>{@code [compatibility.derived]}</td><td>{@code DERIVED}</td></tr>
 *   <tr><td>{@code [compatibility.context]}</td><td>{@code CONTEXT}</td></tr>
 * </table>
 */
public final class RuleSetLoader {

    /** Sections emitted in a stable order. */
    private static final List<String> SECTION_ORDER = List.of(
        "compatibility.statement",
        "compatibility.struct.field",
        "compatibility.enum",
        "compatibility.union",
        "compatibility.stream",
        "compatibility.scalar",
        "compatibility.derived",
        "compatibility.context"
    );

    /** Maps TOML section name → RuleKey enum name prefix. */
    private static final Map<String, String> SECTION_PREFIX = buildSectionPrefix();

    private static Map<String, String> buildSectionPrefix() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("compatibility.statement",    "");
        m.put("compatibility.struct.field", "STRUCT_FIELD");
        m.put("compatibility.enum",         "ENUM");
        m.put("compatibility.union",        "UNION");
        m.put("compatibility.stream",       "STREAM");
        m.put("compatibility.scalar",       "SCALAR");
        m.put("compatibility.derived",      "DERIVED");
        m.put("compatibility.context",      "CONTEXT");
        return Map.copyOf(m);
    }

    private RuleSetLoader() {}

    /**
     * Load rules from the given TOML file, applying overrides on top of
     * {@link RuleSet#defaults()}.
     * Keys absent from the file keep their built-in default.
     *
     * @param path path to the TOML configuration file
     * @return configured {@link RuleSet}
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file contains an unknown rule key or
     *                                  invalid severity
     */
    public static RuleSet load(Path path) throws IOException {
        return parse(Files.readAllLines(path), path.toString());
    }

    /**
     * Generate TOML content that represents every rule in {@code rules}.
     * The output is organised by section and lists all rule keys, making
     * it suitable as a starting point for a custom configuration.
     *
     * <p>Typical use:
     * <pre>
     * String toml = RuleSetLoader.generate(RuleSet.defaults());
     * Files.writeString(Path.of("kafkasql.rules.toml"), toml);
     * </pre>
     *
     * @param rules the rule set to serialise
     * @return TOML text
     */
    public static String generate(RuleSet rules) {
        var sb = new StringBuilder();
        sb.append("# kafkasql.rules.toml — compatibility ruleset\n");
        sb.append("# Generated from built-in defaults — edit severities you want to override.\n");
        sb.append("# Missing entries fall back to the built-in defaults.\n");
        sb.append("# Usage: kafkasql diff --rules <file> v1.kafka v2.kafka\n");
        sb.append("# Valid severities: SAFE  INFO  WARNING  BREAKING\n");

        for (String section : SECTION_ORDER) {
            sb.append("\n[" + section + "]\n");
            String prefix = SECTION_PREFIX.get(section);
            for (RuleKey key : RuleKey.values()) {
                String name = key.name();
                String keyPart;
                if (prefix.isEmpty()) {
                    // [compatibility.statement]: only keys that carry no recognised section prefix
                    boolean belongsElsewhere = SECTION_PREFIX.values().stream()
                        .filter(p -> !p.isEmpty())
                        .anyMatch(p -> name.startsWith(p + "_"));
                    if (belongsElsewhere) continue;
                    keyPart = name.toLowerCase();
                } else {
                    if (!name.startsWith(prefix + "_")) continue;
                    keyPart = name.substring(prefix.length() + 1).toLowerCase();
                }
                sb.append(String.format("%-34s= \"%s\"%n", keyPart + " ", rules.severity(key).name()));
            }
        }

        sb.append("\n# ── Lint rules ──────────────────────────────────────────────────────────────\n");
        sb.append(LintSettingsLoader.generate());

        return sb.toString();
    }

    // ── Private parse core ────────────────────────────────────────────────────

    private static RuleSet parse(List<String> lines, String source) {
        RuleSet rules = RuleSet.defaults();
        String section = null;
        int lineNo = 0;

        for (String raw : lines) {
            lineNo++;
            String line = stripComment(raw).strip();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                section = parseSection(line, lineNo, source);
                // Reserved/unknown sections are silently accepted; their keys are skipped.
                continue;
            }

            if (section == null) {
                throw new IllegalArgumentException(
                    source + ":" + lineNo + ": key found before any section header");
            }

            // Skip keys under reserved or unrecognised sections
            if (!SECTION_PREFIX.containsKey(section)) continue;

            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(
                    source + ":" + lineNo + ": expected 'key = \"VALUE\"', got: " + raw.strip());
            }

            String key    = line.substring(0, eq).strip().toLowerCase();
            String valRaw = line.substring(eq + 1).strip();
            String valStr = unquote(valRaw, lineNo, source);

            String ruleKeyName = toRuleKeyName(section, key);
            RuleKey ruleKey;
            try {
                ruleKey = RuleKey.valueOf(ruleKeyName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    source + ":" + lineNo + ": unknown rule key '"
                    + ruleKeyName + "' (section=[" + section + "] key=" + key + ")");
            }

            DiffSeverity severity;
            try {
                severity = DiffSeverity.valueOf(valStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    source + ":" + lineNo + ": unknown severity '" + valStr
                    + "' — expected SAFE, INFO, WARNING, or BREAKING");
            }

            rules = rules.with(ruleKey, severity);
        }

        return rules;
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

    /** Converts (section, key) to a {@link RuleKey} enum name. */
    private static String toRuleKeyName(String section, String key) {
        String prefix = SECTION_PREFIX.get(section);
        String upper  = key.toUpperCase();
        return prefix.isEmpty() ? upper : prefix + "_" + upper;
    }

    /**
     * Strip a {@code #} comment from the end of a line, respecting quoted strings
     * so that a {@code #} inside a quoted value is not treated as a comment marker.
     */
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
