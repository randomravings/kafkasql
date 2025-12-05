package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface TypeKindDecl
    extends AstNode
    permits EnumDecl,
            ScalarDecl,
            StructDecl,
            UnionDecl,
            DerivedTypeDecl
{ }
