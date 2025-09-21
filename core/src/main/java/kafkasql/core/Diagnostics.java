package kafkasql.core;

import java.util.ArrayList;
import java.util.List;

public class Diagnostics {

  private final List<DiagnosticEntry> entries = new ArrayList<>();

  public void addError(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.ERROR));
  }
  public void addWarning(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.WARNING));
  }
  public void addFatal(Range range, String message) {
    entries.add(new DiagnosticEntry(range, message, DiagnosticEntry.Severity.FATAL));
  }

  public List<DiagnosticEntry> entriesOf(DiagnosticEntry.Severity level) {
    return entries.stream()
        .filter(e -> e.severity().ordinal() == level.ordinal())
        .toList();
  }

  public List<DiagnosticEntry> entriesMin(DiagnosticEntry.Severity level) {
    return entries.stream()
        .filter(e -> e.severity().ordinal() >= level.ordinal())
        .toList();
  }

  public List<DiagnosticEntry> errorsMin() {
    return entriesMin(DiagnosticEntry.Severity.ERROR);
  }

  public List<DiagnosticEntry> errors() {
    return entriesOf(DiagnosticEntry.Severity.ERROR);
  }

  public List<DiagnosticEntry> infos() {
    return entriesOf(DiagnosticEntry.Severity.INFO);
  }

  public List<DiagnosticEntry> infosMin() {
    return entriesMin(DiagnosticEntry.Severity.INFO);
  }

  public List<DiagnosticEntry> warnings() {
    return entriesOf(DiagnosticEntry.Severity.WARNING);
  }

  public List<DiagnosticEntry> warningsMin() {
    return entriesMin(DiagnosticEntry.Severity.WARNING);
  }

  public List<DiagnosticEntry> fatal() {
    return entriesOf(DiagnosticEntry.Severity.FATAL);
  }

  public List<DiagnosticEntry> all() {
    return List.copyOf(entries);
  }

  public boolean hasWarnings() {
    return containsMin(DiagnosticEntry.Severity.WARNING);
  }
  
  public boolean hasErrors() {
    return containsMin(DiagnosticEntry.Severity.ERROR);
  }

  public boolean hasFatal() {
    return containsMin(DiagnosticEntry.Severity.FATAL);
  }

  private boolean containsMin(DiagnosticEntry.Severity level) {
    return entries.stream().anyMatch(e -> e.severity().ordinal() >= level.ordinal());
  }
}