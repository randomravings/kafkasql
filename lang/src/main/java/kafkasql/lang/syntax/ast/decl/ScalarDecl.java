package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.literal.LiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;

public final record ScalarDecl(
    Range range,
    Identifier name,
    PrimitiveTypeNode baseType,
    TypedOptional<LiteralNode> defaultValue,
    TypedOptional<CheckNode> check,
    TypedOptional<DocNode> doc
) implements TypeDecl { }
