package kafkasql.core;

import kafkasql.core.ast.Range;

public final class DiagnosticEntry {
  public enum Severity { INFO, WARNING, ERROR, FATAL }

  private final Range range;
  private final String message;
  private final Severity severity;

  public DiagnosticEntry(Range range, String message, Severity severity) {
    this.range = range == null ? Range.NONE : range;
    this.message = message;
    this.severity = severity;
  }

  public Range range() { return range; }
  public String message() { return message; }
  public Severity severity() { return severity; }

  @Override
  public String toString() {
    var s = range.start();
    var e = range.end();
    return severity + " | " + range.source() + " | [" + s.ln() + ":" + s.ch() + "-" + e.ln() + ":" + e.ch() + "] | " + message;
  }
}