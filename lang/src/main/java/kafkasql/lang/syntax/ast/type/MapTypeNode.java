package kafkasql.lang.syntax.ast.type;

import kafkasql.lang.diagnostics.Range;

public final record MapTypeNode (
    Range range,
    PrimitiveTypeNode keyType,
    TypeNode valueType
) implements CompositeTypeNode { }
