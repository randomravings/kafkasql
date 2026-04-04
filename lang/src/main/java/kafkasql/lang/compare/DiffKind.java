package kafkasql.lang.compare;

public enum DiffKind {
    /** Exists in the left script only — was removed. */
    LEFT_ONLY,
    /** Exists in the right script only — was added. */
    RIGHT_ONLY,
    /** Identical in both scripts. */
    UNCHANGED,
    /** Present in both scripts but structurally different. */
    MODIFIED
}
