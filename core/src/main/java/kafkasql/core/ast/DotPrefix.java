package kafkasql.core.ast;

import kafkasql.core.Range;

public final record DotPrefix(Range range) implements AstNode { }
