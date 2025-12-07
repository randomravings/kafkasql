package kafkasql.runtime.diagnostics;

public final record DiagnosticEntry(
    Range range,
    DiagnosticKind kind,
    DiagnosticCode code,
    Severity severity,
    String message
) {

    @Override
    public String toString() {
        var f = range.from();
        var t = range.to();
        return severity + " | " + kind + " | " + code + " | " +
            range.source() + " | [" + f.ln() + ":" + f.ch() + "-" +
            t.ln() + ":" + t.ch() + "] | " + message;
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR,
        FATAL
    }
}
