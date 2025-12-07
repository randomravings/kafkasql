package kafkasql.lang.syntax.ast.type;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.QName;

public final record ComplexTypeNode(
    Range range,
    QName name
) implements TypeNode { }
