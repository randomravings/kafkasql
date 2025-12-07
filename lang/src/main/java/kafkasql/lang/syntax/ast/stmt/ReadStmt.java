package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.misc.QName;

public record ReadStmt(
    Range range,
    QName stream,
    AstListNode<ReadTypeBlock> blocks
) implements Stmt { }
