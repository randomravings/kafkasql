package streamsql.ast;

public final record ReadStmt(Range range, QName stream, AstListNode<ReadTypeBlock> blocks) implements Stmt {}
