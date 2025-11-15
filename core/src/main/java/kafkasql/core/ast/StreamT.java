package kafkasql.core.ast;

public final record StreamT(Range range, QName qName, AstListNode<StreamType> types, AstOptionalNode<Doc> doc) implements AstNode {}
