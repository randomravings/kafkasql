package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;

public record StructLiteralNode(
    Range range,
    TypedList<StructFieldLiteralNode> fields
) implements ComplexLiteralNode { }
