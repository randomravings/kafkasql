package kafkasql.lang.syntax.ast.show;

/**
 * Target type for SHOW statements (CONTEXTS, TYPES, or STREAMS).
 */
public enum ShowTarget {
    CONTEXTS,
    TYPES,
    STREAMS
}
