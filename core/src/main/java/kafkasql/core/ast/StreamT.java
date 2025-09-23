package kafkasql.core.ast;

import kafkasql.core.Range;

public final record StreamT(Range range, QName qName, AstListNode<StreamType> types, AstOptionalNode<Doc> doc) implements AstNode {}
