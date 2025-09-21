package kafkasql.core.ast;

import kafkasql.core.Range;

public final record ReadStmt(Range range, QName stream, AstListNode<ReadTypeBlock> blocks) implements Stmt {}
