package streamsql.ast;

import java.util.List;

public final record WriteStmt(Range range, QName stream, Identifier alias, List<StructV> values) implements Stmt { }