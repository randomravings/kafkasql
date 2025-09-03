package streamsql;

import java.util.ArrayList;
import java.util.List;

public class Diagnostics {
  private String fatalError = "";
  private final List<String> errors = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  public void fatal(String m) { fatalError = m; errors.add(m); }
  public void error(String m) { errors.add(m); }
  public void warn(String m)  { warnings.add(m); }
  public boolean ok() { return fatalError.isEmpty() && errors.isEmpty(); }
  public boolean hasErrors() { return !fatalError.isEmpty() || !errors.isEmpty(); }
  public boolean hasWarnings() { return !warnings.isEmpty(); }
  public boolean hasFatal() { return !fatalError.isEmpty(); }
  public String fatal() { return fatalError; }
  public List<String> errors()   { return errors; }
  public List<String> warnings() { return warnings; }
  public List<String> all() {
    var all = new ArrayList<String>(errors.size()+warnings.size());
    all.addAll(errors); all.addAll(warnings); return all;
  }
}