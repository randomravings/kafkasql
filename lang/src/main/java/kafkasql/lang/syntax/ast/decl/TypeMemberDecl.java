package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public sealed interface TypeMemberDecl
    extends AstNode
    permits EnumSymbolDecl,
            StructFieldDecl,
            UnionMemberDecl
{
    Identifier name();
    TypedOptional<DocNode> doc();
}
