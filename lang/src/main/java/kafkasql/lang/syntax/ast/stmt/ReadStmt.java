package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.QName;

public record ReadStmt(
    Range range,
    QName stream,
    TypedList<ReadTypeBlock> blocks
) implements Stmt { }
