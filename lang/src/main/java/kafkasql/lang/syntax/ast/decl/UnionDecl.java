package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.literal.UnionLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record UnionDecl(
    Range range,
    Identifier name,
    TypedList<UnionMemberDecl> members,
    TypedOptional<UnionLiteralNode> defaultValue,
    TypedOptional<DocNode> doc
) implements TypeDecl { }
