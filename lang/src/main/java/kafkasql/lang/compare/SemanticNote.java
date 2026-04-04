package kafkasql.lang.compare;

/**
 * An annotation produced by {@link SemanticEnricher} and attached to a diff node.
 *
 * @param severity  How significant the change is.
 * @param aspect    Which structural aspect the note refers to (e.g. "type", "nullable").
 * @param message   Human-readable description suitable for LSP hover / diagnostic text.
 */
public record SemanticNote(
    DiffSeverity severity,
    String aspect,
    String message
) {}
