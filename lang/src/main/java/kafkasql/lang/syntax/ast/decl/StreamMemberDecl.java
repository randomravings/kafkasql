package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.DistributeNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public sealed interface StreamMemberDecl
    extends AstNode
    permits StreamMemberInlineDecl,
            StreamMemberRefDecl
{
    Identifier name();
    TypedOptional<DistributeNode> distribute();
    TypedOptional<Identifier> timestampField();
    TypedList<CheckNode> checks();
}
