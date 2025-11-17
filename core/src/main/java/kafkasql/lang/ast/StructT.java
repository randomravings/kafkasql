package kafkasql.lang.ast;

public final record StructT(Range range, QName qName, AstListNode<Field> fieldList, AstOptionalNode<Doc> doc) implements ComplexT {}