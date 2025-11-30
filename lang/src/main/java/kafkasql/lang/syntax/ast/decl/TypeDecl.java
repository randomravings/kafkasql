package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public sealed interface TypeDecl
    extends Decl
    permits EnumDecl,
            ScalarDecl,
            StructDecl,
            UnionDecl 
{
    Identifier name();
    TypedOptional<DocNode> doc();
}
