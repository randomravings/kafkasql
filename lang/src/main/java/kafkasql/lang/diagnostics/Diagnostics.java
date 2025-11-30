package kafkasql.lang.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Diagnostics {

    private final List<DiagnosticEntry> entries = new ArrayList<>();

    private boolean hasFatal = false;
    private boolean hasError = false;
    private boolean hasWarning = false;

    // ============================================================
    // CORE REPORT METHOD
    // ============================================================
    public void report(
        Range range,
        DiagnosticKind kind,
        DiagnosticCode code,
        DiagnosticEntry.Severity severity,
        String message
    ) {
        DiagnosticEntry e = new DiagnosticEntry(range, kind, code, severity, message);
        entries.add(e);

        switch (severity) {
            case WARNING:
                hasWarning = true;
                break;
            case ERROR:
                hasError = true;
                break;
            case FATAL:
                hasError = true;
                hasFatal = true;
                break;
            case INFO:
                // No-op
                break;
        }
    }

    // ============================================================
    // CONVENIENCE HELPERS
    // ============================================================

    public void info(Range r, DiagnosticKind kind, DiagnosticCode code, String msg) {
        report(r, kind, code, DiagnosticEntry.Severity.INFO, msg);
    }

    public void warning(Range r, DiagnosticKind kind, DiagnosticCode code, String msg) {
        report(r, kind, code, DiagnosticEntry.Severity.WARNING, msg);
    }

    public void error(Range r, DiagnosticKind kind, DiagnosticCode code, String msg) {
        report(r, kind, code, DiagnosticEntry.Severity.ERROR, msg);
    }

    public void fatal(Range r, DiagnosticKind kind, DiagnosticCode code, String msg) {
        report(r, kind, code, DiagnosticEntry.Severity.FATAL, msg);
    }

    // Specialized short-hands
    public void syntaxError(Range r, String msg) {
        error(r, DiagnosticKind.PARSER, DiagnosticCode.SYNTAX_ERROR, msg);
    }

    public void lexerError(Range r, String msg) {
        error(r, DiagnosticKind.LEXER, DiagnosticCode.UNEXPECTED_TOKEN, msg);
    }

    public void semantic(Range r, DiagnosticCode code, String msg) {
        error(r, DiagnosticKind.SEMANTIC, code, msg);
    }

    public void internal(Range r, String msg) {
        error(r, DiagnosticKind.INTERNAL, DiagnosticCode.INTERNAL_ERROR, msg);
    }

    // ============================================================
    // STATUS QUERIES
    // ============================================================

    public boolean hasError()  { return hasError; }
    public boolean hasFatal()  { return hasFatal; }
    public boolean hasWarning(){ return hasWarning; }

    // ============================================================
    // RETRIEVAL
    // ============================================================

    public List<DiagnosticEntry> all() {
        return List.copyOf(entries);
    }

    public List<DiagnosticEntry> ofKind(DiagnosticKind k) {
        return entries.stream().filter(e -> e.kind() == k).collect(Collectors.toList());
    }

    public List<DiagnosticEntry> ofSeverity(DiagnosticEntry.Severity s) {
        return entries.stream().filter(e -> e.severity() == s).collect(Collectors.toList());
    }

    public List<DiagnosticEntry> errors() {
        return entries.stream()
            .filter(e -> e.severity() == DiagnosticEntry.Severity.ERROR ||
                        e.severity() == DiagnosticEntry.Severity.FATAL)
            .collect(Collectors.toList());
    }
}