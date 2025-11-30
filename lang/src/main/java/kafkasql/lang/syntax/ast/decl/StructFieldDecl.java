package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.literal.LiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final record StructFieldDecl(
    Range range,
    Identifier name,
    TypeNode type,
    boolean nullable,
    TypedOptional<LiteralNode> defaultValue,
    TypedOptional<DocNode> doc
) implements TypeMemberDecl { }
