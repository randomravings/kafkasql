package kafkasql.lang.ast;

public final record UnionT(Range range, QName qName, AstListNode<UnionMember> types, AstOptionalNode<UnionV> defaultvalue, AstOptionalNode<Doc> doc) implements ComplexT { }
