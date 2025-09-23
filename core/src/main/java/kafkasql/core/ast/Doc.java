package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Doc(Range range, String content) implements AstNode { }
