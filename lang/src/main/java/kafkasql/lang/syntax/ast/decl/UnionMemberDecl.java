package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final record UnionMemberDecl(
    Range range,
    Identifier name,
    TypeNode type,
    TypedOptional<DocNode> doc
) implements TypeMemberDecl { }
