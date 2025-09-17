package streamsql.ast;

public record Identifier(Range range, String name) implements AstNode, Comparable<Identifier> {

  @Override
  public int compareTo(Identifier o) {
    return name.compareTo(o.name);
  }
}
