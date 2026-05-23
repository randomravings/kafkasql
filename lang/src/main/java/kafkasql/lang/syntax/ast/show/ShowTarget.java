package kafkasql.lang.syntax.ast.show;

/**
 * Target type for SHOW statements (CONTEXTS, TYPES, STREAMS, or USERS).
 */
public enum ShowTarget {
    CONTEXTS,
    TYPES,
    STREAMS,
    USERS
}
