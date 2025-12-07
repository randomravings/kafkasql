package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;

public record UnionLiteralNode(
    Range range,
    QName unionName,
    Identifier memberName,
    LiteralNode value
) implements ComplexLiteralNode { }
