package streamsql.ast;

import java.util.List;

public final record WriteStmt(QName stream, Identifier alias, List<StructV> values) implements Stmt { }