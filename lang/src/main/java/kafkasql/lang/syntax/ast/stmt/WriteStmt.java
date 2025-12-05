package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;

public record WriteStmt(
    Range range,
    QName stream,
    Identifier alias,
    AstListNode<StructLiteralNode> values
) implements Stmt { }
