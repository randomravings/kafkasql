package streamsql.ast;

public final record Symbol(Identifier name, AnyT type) implements Expr, Comparable<Symbol> {
  @Override
  public int compareTo(Symbol o) {
    return name.compareTo(o.name);
  }
}
