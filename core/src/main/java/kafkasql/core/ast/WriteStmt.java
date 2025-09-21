package kafkasql.core.ast;

import kafkasql.core.Range;

public final record WriteStmt(Range range, QName stream, Identifier alias, ListV values) implements Stmt { }