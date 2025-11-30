package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public sealed interface Decl
    extends AstNode
    permits ContextDecl,
            StreamDecl,
            TypeDecl
{
    public Identifier name();
    public TypedOptional<DocNode> doc();
}
