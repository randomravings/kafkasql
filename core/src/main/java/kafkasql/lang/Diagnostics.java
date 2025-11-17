package kafkasql.lang;

import java.util.ArrayList;
import java.util.List;

import kafkasql.lang.ast.Range;

public class Diagnostics {

  private final List<DiagnosticEntry> entries = new ArrayList<>();

  private boolean hasFatal = false;
  private boolean hasError = false;
  private boolean hasWarning = false;

  // Add methods with Range parameter
  public void info(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.INFO));
  }

  public void warning(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.WARNING));
    hasWarning = true;
  }

  public void error(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.ERROR));
    hasError = true;
  }

  public void fatal(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.FATAL));
    hasFatal = true;
    hasError = true;  // Fatal implies error
  }

  // Status check methods
  public boolean hasWarning() {
    return this.hasWarning;
  }
  
  public boolean hasError() {
    return this.hasError;  // Returns true for error OR fatal
  }

  public boolean hasFatal() {
    return this.hasFatal;
  }

  // Retrieval methods
  public List<DiagnosticEntry> get() {
    return List.copyOf(entries);
  }

  public List<DiagnosticEntry> get(DiagnosticEntry.Severity level) {
    return entries.stream()
        .filter(e -> e.severity() == level)
        .toList();
  }

  public List<DiagnosticEntry> min(DiagnosticEntry.Severity level) {
    return entries.stream()
        .filter(e -> e.severity().ordinal() >= level.ordinal())
        .toList();
  }

  public List<DiagnosticEntry> errors() {
    return min(DiagnosticEntry.Severity.ERROR);  // Returns ERROR and FATAL
  }
}