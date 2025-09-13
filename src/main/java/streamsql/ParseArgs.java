package streamsql;

public class ParseArgs {
  public final boolean resolveIncludes;
  public final boolean trace;

  public ParseArgs(boolean resolveIncludes, boolean trace) {
    this.resolveIncludes = resolveIncludes;
    this.trace = trace;
  }
}
