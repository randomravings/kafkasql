package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;

public final record UnionDecl(
    Range range,
    AstListNode<UnionMemberDecl> members
) implements TypeKindDecl { }
