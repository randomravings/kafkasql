package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;

public record WriteStmt(
    Range range,
    QName stream,
    Identifier alias,
    TypedList<StructLiteralNode> values
) implements Stmt { }
