package kafkasql.core.ast;
import java.util.LinkedHashMap;

public abstract class AstMapNode<K extends AstNode, V extends AstNode> extends LinkedHashMap<K, AstMapEntryNode<K, V>> implements AstNode {
  private final Range range;

  public AstMapNode(Range range) {
    this.range = range;
  }

  public Range range() {
    return range;
  }
}
