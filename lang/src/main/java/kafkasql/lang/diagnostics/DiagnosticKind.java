package kafkasql.lang.diagnostics;

public enum DiagnosticKind {
    LEXER,
    PARSER,
    INCLUDE,
    RESOLVE,
    TYPE,
    SEMANTIC,
    RUNTIME,
    INTERNAL
}