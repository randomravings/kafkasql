package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.literal.NumberLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record EnumSymbolDecl(
    Range range,
    Identifier name,
    NumberLiteralNode value,
    TypedOptional<DocNode> doc
) implements TypeMemberDecl { }
