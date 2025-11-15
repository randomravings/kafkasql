package kafkasql.core.ast;

public final record Doc(Range range, String content) implements AstNode { }
