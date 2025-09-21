package kafkasql.core.ast;

import java.util.HashMap;
import java.util.Map;

import kafkasql.core.Range;

public abstract class AstMapNode<K extends AstNode, V extends AstNode> extends HashMap<K, V> implements AstNode {
  public final Range range;

  public AstMapNode(Range range) {
    super();
    this.range = range;
  }

  public AstMapNode(Range range, Map<K, V> entries) {
    super(entries);
    this.range = range;
  }

  public Range range() {
    return range;
  }
}
