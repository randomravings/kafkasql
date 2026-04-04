package kafkasql.linter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import kafkasql.runtime.diagnostics.DiagnosticEntry;

/**
 * Per-rule severity overrides for the lint engine.
 *
 * <p>Rules absent from the map use their {@link RuleMetadata#defaultSeverity()}.
 * Rules mapped to {@code null} are disabled (suppressed entirely).
 *
 * <p>Keys are lower-cased {@link RuleMetadata#qualifiedId()} values, e.g.
 * {@code "naming/pascal-case-types"}.
 *
 * <p>Configured via {@code [lint.*]} sections in {@code kafkasql.rules.toml} or
 * {@code *.proj.toml}:
 * <pre>
 * [lint.naming]
 * pascal-case-types           = "WARNING"
 * pascal-case-fields          = "OFF"      # disable entirely
 * screaming-snake-case-enums  = "ERROR"
 * exact-case-member-reference = "INFO"
 * </pre>
 */
public final class LintSettings {

    /**
     * Singleton empty settings — every rule uses its built-in default severity.
     */
    private static final LintSettings DEFAULTS = new LintSettings(Map.of());

    /**
     * Map from lower-cased qualified rule id to effective severity.
     * A {@code null} value means the rule is disabled (OFF).
     */
    private final Map<String, DiagnosticEntry.Severity> overrides;

    private LintSettings(Map<String, DiagnosticEntry.Severity> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * Returns settings with no overrides — all rules use their default severity.
     */
    public static LintSettings defaults() {
        return DEFAULTS;
    }

    /**
     * Returns {@code true} if the rule identified by {@code qualifiedId} is enabled.
     * A rule is disabled when its configured value is {@code "OFF"} (stored as {@code null}).
     *
     * @param qualifiedId lower-cased qualified id, e.g. {@code "naming/pascal-case-types"}
     */
    public boolean isEnabled(String qualifiedId) {
        if (!overrides.containsKey(qualifiedId.toLowerCase())) return true;
        return overrides.get(qualifiedId.toLowerCase()) != null;
    }

    /**
     * Returns the effective severity for the given rule metadata.
     * Returns the configured override when present, otherwise the rule's
     * {@link RuleMetadata#defaultSeverity()}.
     *
     * <p>Callers should first check {@link #isEnabled(String)} — calling this for
     * a disabled rule returns the rule's default severity, which is meaningless.
     *
     * @param metadata rule metadata
     * @return effective severity
     */
    public DiagnosticEntry.Severity effectiveSeverity(RuleMetadata metadata) {
        DiagnosticEntry.Severity override = overrides.get(metadata.qualifiedId().toLowerCase());
        if (override == null && overrides.containsKey(metadata.qualifiedId().toLowerCase())) {
            // Explicitly mapped to null → disabled; fall back to default (caller should not reach here)
            return metadata.defaultSeverity();
        }
        return override != null ? override : metadata.defaultSeverity();
    }

    /**
     * Returns a copy of these settings with the given rule overridden.
     *
     * @param qualifiedId lower-cased qualified rule id
     * @param severity    new severity, or {@code null} to disable the rule
     */
    public LintSettings with(String qualifiedId, DiagnosticEntry.Severity severity) {
        Map<String, DiagnosticEntry.Severity> copy = new HashMap<>(overrides);
        copy.put(qualifiedId.toLowerCase(), severity);
        return new LintSettings(copy);
    }

    /**
     * Returns a copy of these settings with the given rule disabled.
     */
    public LintSettings disable(String qualifiedId) {
        return with(qualifiedId, null);
    }

    @Override
    public String toString() {
        return "LintSettings" + overrides;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LintSettings ls && Objects.equals(overrides, ls.overrides);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(overrides);
    }
}
