package streamsql.ast;

import java.util.List;

public final record WriteStmt(QName stream, Identifier alias, List<Path> projection, List<Tuple> rows) implements DmlStmt { }