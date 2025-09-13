package streamsql.ast;

import java.util.List;

public final record WriteStmt(QName stream, Identifier alias, Projection projection, List<ListV> rows) implements Stmt { }