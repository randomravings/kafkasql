package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;

public record EnumLiteralNode(
    Range range,
    QName enumName,
    Identifier symbol
) implements ComplexLiteralNode { }
