package kafkasql.core;

import kafkasql.core.ast.Range;

public class AstBuildException extends RuntimeException {
  private final Range range;

  public AstBuildException(Range range, String message) {
    super(message);
    this.range = range;
  }
  public Range range() {
    return range;
  }
}
