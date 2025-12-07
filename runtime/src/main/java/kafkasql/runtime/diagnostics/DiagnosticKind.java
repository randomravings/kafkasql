package kafkasql.runtime.diagnostics;

public enum DiagnosticKind {
    LEXER,
    PARSER,
    INCLUDE,
    RESOLVE,
    TYPE,
    SEMANTIC,
    RUNTIME,
    LINT,
    INTERNAL
}