package streamsql.ast;

import java.util.List;

public final record ReadStmt(QName stream, List<ReadSelection> blocks) implements Stmt {}
