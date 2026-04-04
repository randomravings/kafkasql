package kafkasql.lang.compare;

import java.util.EnumMap;

/**
 * Configurable set of severity rules used by {@link SemanticEnricher}.
 *
 * <p>Each {@link RuleKey} maps to a {@link DiffSeverity} that the enricher will assign
 * when that scenario is detected.  The factory method {@link #defaults()} returns the
 * built-in rule set that reflects KafkaSQL's immutable-log semantics.  Individual rules
 * can be overridden non-destructively via {@link #with(RuleKey, DiffSeverity)}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use built-in defaults
 * SemanticEnricher.enrich(diff);
 *
 * // Override individual rules
 * RuleSet lenient = RuleSet.defaults()
 *     .with(RuleKey.STRUCT_FIELD_TYPE_CHANGED, DiffSeverity.WARNING)
 *     .with(RuleKey.ENUM_SYMBOL_ADDED,         DiffSeverity.SAFE);
 * SemanticEnricher.enrich(diff, lenient);
 * }</pre>
 *
 * <p>Instances are immutable.  {@link #with(RuleKey, DiffSeverity)} returns a new
 * instance; the original is never modified.
 */
public final class RuleSet {

    private final EnumMap<RuleKey, DiffSeverity> rules;

    private RuleSet(EnumMap<RuleKey, DiffSeverity> rules) {
        this.rules = rules;
    }

    /**
     * Return the configured severity for the given rule key.
     * Falls back to {@link DiffSeverity#INFO} for any key not explicitly configured.
     */
    public DiffSeverity severity(RuleKey key) {
        return rules.getOrDefault(key, DiffSeverity.INFO);
    }

    /**
     * Return a new {@code RuleSet} instance with {@code key} overridden to
     * {@code severity}.  This instance is not modified.
     */
    public RuleSet with(RuleKey key, DiffSeverity severity) {
        EnumMap<RuleKey, DiffSeverity> copy = new EnumMap<>(rules);
        copy.put(key, severity);
        return new RuleSet(copy);
    }

    /** Return the default rule set (KafkaSQL immutable-log semantics). */
    public static RuleSet defaults() {
        return DEFAULT;
    }

    // ── Default rule set ──────────────────────────────────────────────────────

    private static final RuleSet DEFAULT = buildDefaults();

    private static RuleSet buildDefaults() {
        EnumMap<RuleKey, DiffSeverity> m = new EnumMap<>(RuleKey.class);

        // Statement level
        m.put(RuleKey.DECL_REMOVED,      DiffSeverity.BREAKING);
        m.put(RuleKey.DECL_ADDED,        DiffSeverity.SAFE);
        m.put(RuleKey.DECL_KIND_CHANGED, DiffSeverity.BREAKING);
        m.put(RuleKey.DROP_ADDED,        DiffSeverity.BREAKING);
        m.put(RuleKey.DROP_REMOVED,      DiffSeverity.SAFE);
        m.put(RuleKey.ALTER_ADDED,       DiffSeverity.WARNING);
        m.put(RuleKey.ALTER_REMOVED,     DiffSeverity.INFO);

        // Struct field
        m.put(RuleKey.STRUCT_FIELD_REMOVED,                       DiffSeverity.BREAKING);
        m.put(RuleKey.STRUCT_FIELD_SOFT_DROPPED,                  DiffSeverity.SAFE);
        m.put(RuleKey.STRUCT_FIELD_ADDED_OPTIONAL,                DiffSeverity.SAFE);
        m.put(RuleKey.STRUCT_FIELD_ADDED_REQUIRED,                DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_TYPE_CHANGED,                  DiffSeverity.BREAKING);
        m.put(RuleKey.STRUCT_FIELD_NULLABLE_ADDED,                DiffSeverity.BREAKING);
        m.put(RuleKey.STRUCT_FIELD_NULLABLE_REMOVED_WITH_DEFAULT, DiffSeverity.SAFE);
        m.put(RuleKey.STRUCT_FIELD_NULLABLE_REMOVED_NO_DEFAULT,   DiffSeverity.BREAKING);
        m.put(RuleKey.STRUCT_FIELD_DEFAULT_ADDED,                 DiffSeverity.SAFE);
        m.put(RuleKey.STRUCT_FIELD_DEFAULT_REMOVED,               DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_DEFAULT_CHANGED,               DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_CHECK_ADDED,                   DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_CHECK_REMOVED,                 DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_CHECK_CHANGED,                 DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_DROPPED_ADDED,                 DiffSeverity.SAFE);
        m.put(RuleKey.STRUCT_FIELD_DROPPED_REMOVED,               DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_DOC_CHANGED,                   DiffSeverity.INFO);
        m.put(RuleKey.STRUCT_FIELD_DISTRIBUTE_CHANGED,            DiffSeverity.BREAKING);
        m.put(RuleKey.STRUCT_FIELD_TIMESTAMP_CHANGED,             DiffSeverity.BREAKING);
        m.put(RuleKey.STRUCT_FIELD_CONSTRAINT_ADDED,              DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_CONSTRAINT_REMOVED,            DiffSeverity.WARNING);
        m.put(RuleKey.STRUCT_FIELD_CONSTRAINT_CHANGED,            DiffSeverity.WARNING);

        // Enum
        m.put(RuleKey.ENUM_BASE_TYPE_CHANGED,    DiffSeverity.BREAKING);
        m.put(RuleKey.ENUM_SYMBOL_REMOVED,       DiffSeverity.BREAKING);
        m.put(RuleKey.ENUM_SYMBOL_ADDED,         DiffSeverity.WARNING);
        m.put(RuleKey.ENUM_SYMBOL_VALUE_CHANGED, DiffSeverity.BREAKING);
        m.put(RuleKey.ENUM_SYMBOL_DOC_CHANGED,   DiffSeverity.INFO);

        // Union
        m.put(RuleKey.UNION_MEMBER_REMOVED,      DiffSeverity.BREAKING);
        m.put(RuleKey.UNION_MEMBER_ADDED,        DiffSeverity.WARNING);
        m.put(RuleKey.UNION_MEMBER_TYPE_CHANGED, DiffSeverity.BREAKING);
        m.put(RuleKey.UNION_MEMBER_DOC_CHANGED,  DiffSeverity.INFO);

        // Stream
        m.put(RuleKey.STREAM_MEMBER_REMOVED,        DiffSeverity.BREAKING);
        m.put(RuleKey.STREAM_MEMBER_ADDED,          DiffSeverity.SAFE);
        m.put(RuleKey.STREAM_MEMBER_KIND_CHANGED,   DiffSeverity.BREAKING);
        m.put(RuleKey.STREAM_MEMBER_STRUCT_CHANGED, DiffSeverity.WARNING);
        m.put(RuleKey.STREAM_MEMBER_CHANGED,        DiffSeverity.WARNING);

        // Scalar
        m.put(RuleKey.SCALAR_TYPE_CHANGED, DiffSeverity.BREAKING);
        m.put(RuleKey.SCALAR_DOC_CHANGED,  DiffSeverity.INFO);

        // Derived
        m.put(RuleKey.DERIVED_BASE_CHANGED, DiffSeverity.BREAKING);
        m.put(RuleKey.DERIVED_DOC_CHANGED,  DiffSeverity.INFO);

        // Context
        m.put(RuleKey.CONTEXT_CHANGED,     DiffSeverity.INFO);
        m.put(RuleKey.CONTEXT_DOC_CHANGED, DiffSeverity.INFO);

        return new RuleSet(m);
    }
}
