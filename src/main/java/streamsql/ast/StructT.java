package streamsql.ast;

public final record StructT(Range range, QName qName, AstListNode<Field> fieldList) implements ComplexT {}