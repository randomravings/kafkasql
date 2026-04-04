package kafkasql.lang.compare;

/** Semantic severity of a structural change, used by the enrichment pass. */
public enum DiffSeverity {
    /** Change is incompatible with existing consumers (e.g. type narrowing, field removed). */
    BREAKING,
    /** Change requires attention but may not break existing consumers (e.g. new required field). */
    WARNING,
    /** Change is backward-compatible (e.g. nullable field added, type widened). */
    SAFE,
    /** Informational only (e.g. doc comment changed, field reordered). */
    INFO
}
