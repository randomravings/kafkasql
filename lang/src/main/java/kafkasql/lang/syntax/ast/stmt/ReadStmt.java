package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstOptionalNode;
import kafkasql.lang.syntax.ast.misc.QName;

public record ReadStmt(
    Range range,
    QName stream,
    AstOptionalNode<ReadMode> mode,
    AstOptionalNode<StopAfter> stopAfter,
    AstListNode<ReadTypeBlock> blocks
) implements Stmt { }
