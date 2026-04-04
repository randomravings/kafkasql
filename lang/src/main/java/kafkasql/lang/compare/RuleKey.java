package kafkasql.lang.compare;

/**
 * Identifies a specific semantic rule point within {@link SemanticEnricher}.
 * Each key corresponds to one configurable classification decision.
 * Passed to {@link RuleSet#severity(RuleKey)} to determine the
 * {@link DiffSeverity} assigned to that scenario.
 *
 * <p>The default severity for every key is defined in
 * {@link RuleSet#defaults()} and reflects KafkaSQL's immutable-log semantics.
 * Override individual keys via {@link RuleSet#with(RuleKey, DiffSeverity)}.
 */
public enum RuleKey {

    // ── Statement level ───────────────────────────────────────────────────────

    /** A CREATE declaration present on the left is absent on the right. */
    DECL_REMOVED,
    /** A CREATE declaration is new on the right. */
    DECL_ADDED,
    /** The declaration kind changed (e.g. STRUCT → ENUM). */
    DECL_KIND_CHANGED,
    /** A DROP statement was added on the right. */
    DROP_ADDED,
    /** A DROP statement was removed on the right. */
    DROP_REMOVED,
    /** An ALTER statement was added on the right. */
    ALTER_ADDED,
    /** An ALTER statement was removed on the right. */
    ALTER_REMOVED,

    // ── Struct field ──────────────────────────────────────────────────────────

    /** Field is present on the left only, without a {@code DroppedNode} marker. */
    STRUCT_FIELD_REMOVED,
    /** Field is present on the left only, with a {@code DroppedNode} marker (soft-drop). */
    STRUCT_FIELD_SOFT_DROPPED,
    /** New field added on the right that is nullable or has a default value. */
    STRUCT_FIELD_ADDED_OPTIONAL,
    /** New required field added on the right with no default value. */
    STRUCT_FIELD_ADDED_REQUIRED,
    /** Field type changed (name and type are immutable on the Kafka log). */
    STRUCT_FIELD_TYPE_CHANGED,
    /** Nullability weakened: NOT NULL → NULL. */
    STRUCT_FIELD_NULLABLE_ADDED,
    /** Nullability tightened: NULL → NOT NULL, and the right side carries a DEFAULT. */
    STRUCT_FIELD_NULLABLE_REMOVED_WITH_DEFAULT,
    /** Nullability tightened: NULL → NOT NULL, with no DEFAULT present on the right. */
    STRUCT_FIELD_NULLABLE_REMOVED_NO_DEFAULT,
    /** DEFAULT value added to an existing field. */
    STRUCT_FIELD_DEFAULT_ADDED,
    /** DEFAULT value removed from an existing field. */
    STRUCT_FIELD_DEFAULT_REMOVED,
    /** DEFAULT value changed on an existing field. */
    STRUCT_FIELD_DEFAULT_CHANGED,
    /** CHECK constraint added to an existing field. */
    STRUCT_FIELD_CHECK_ADDED,
    /** CHECK constraint removed from an existing field. */
    STRUCT_FIELD_CHECK_REMOVED,
    /** CHECK constraint expression changed on an existing field. */
    STRUCT_FIELD_CHECK_CHANGED,
    /** {@code DroppedNode} marker added to field (via {@code ALTER TYPE … DROP}). */
    STRUCT_FIELD_DROPPED_ADDED,
    /** {@code DroppedNode} marker removed from a previously soft-dropped field. */
    STRUCT_FIELD_DROPPED_REMOVED,
    /** Documentation comment changed on a struct field. */
    STRUCT_FIELD_DOC_CHANGED,
    /** {@code DISTRIBUTE BY} keys changed. */
    STRUCT_FIELD_DISTRIBUTE_CHANGED,
    /** {@code TIMESTAMP BY} field changed. */
    STRUCT_FIELD_TIMESTAMP_CHANGED,
    /** A named constraint was added to a field. */
    STRUCT_FIELD_CONSTRAINT_ADDED,
    /** A named constraint was removed from a field. */
    STRUCT_FIELD_CONSTRAINT_REMOVED,
    /** A named constraint expression changed on a field. */
    STRUCT_FIELD_CONSTRAINT_CHANGED,

    // ── Enum ──────────────────────────────────────────────────────────────────

    /** The underlying integer base type of an enum changed (affects wire encoding width). */
    ENUM_BASE_TYPE_CHANGED,
    /** An enum symbol was removed. */
    ENUM_SYMBOL_REMOVED,
    /** A new enum symbol was added. */
    ENUM_SYMBOL_ADDED,
    /** The integer discriminant value of an enum symbol changed. */
    ENUM_SYMBOL_VALUE_CHANGED,
    /** Documentation comment changed on an enum symbol. */
    ENUM_SYMBOL_DOC_CHANGED,

    // ── Union ─────────────────────────────────────────────────────────────────

    /** A union member was removed. */
    UNION_MEMBER_REMOVED,
    /** A new union member was added. */
    UNION_MEMBER_ADDED,
    /** The type of a union member changed (immutable on the log). */
    UNION_MEMBER_TYPE_CHANGED,
    /** Documentation comment changed on a union member. */
    UNION_MEMBER_DOC_CHANGED,

    // ── Stream ────────────────────────────────────────────────────────────────

    /** A stream message-type member was removed. */
    STREAM_MEMBER_REMOVED,
    /** A new stream message-type member was added. */
    STREAM_MEMBER_ADDED,
    /** The declaration kind of a stream message type changed. */
    STREAM_MEMBER_KIND_CHANGED,
    /** Inner struct of a stream message type has field-level changes. */
    STREAM_MEMBER_STRUCT_CHANGED,
    /** A stream message type changed in a way not covered by more specific rules. */
    STREAM_MEMBER_CHANGED,

    // ── Scalar ────────────────────────────────────────────────────────────────

    /** The underlying type of a scalar type changed. */
    SCALAR_TYPE_CHANGED,
    /** Documentation comment changed on a scalar type. */
    SCALAR_DOC_CHANGED,

    // ── Derived ───────────────────────────────────────────────────────────────

    /** The base-type target of a derived type changed. */
    DERIVED_BASE_CHANGED,
    /** Documentation comment changed on a derived type. */
    DERIVED_DOC_CHANGED,

    // ── Context ───────────────────────────────────────────────────────────────

    /** A context declaration property changed (non-doc). */
    CONTEXT_CHANGED,
    /** Documentation comment changed on a context declaration. */
    CONTEXT_DOC_CHANGED,
}
