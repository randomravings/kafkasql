package kafkasql.lang.ast;

public final record QName(Range range, AstOptionalNode<DotPrefix> dotPrefix, IdentifierList parts) implements AstNode {

  public static final QName ROOT = new QName(Range.NONE, AstOptionalNode.empty(), IdentifierList.EMPTY);

  public static QName of(Identifier id) {
    return new QName(id.range(), AstOptionalNode.empty(), new IdentifierList(id.range(), AstListNode.of(id)));
  }

  public Boolean isRoot() {
    return parts.isEmpty();
  }

  public IdentifierList parts() {
    return parts;
  }

  public String context() {
    return parts.size() > 1 ? String.join(".", parts.subList(0, parts.size() - 1).stream().map(Identifier::name).toList()) : "";
  }

  public String name() {
    return parts.size() > 0 ? parts.getLast().name() : "";
  }

  public String fullName() {
    return String.join(".", parts.stream().map(Identifier::name).toList());
  }
}