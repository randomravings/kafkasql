package kafkasql.lang.syntax.ast.decl;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record StreamMemberDecl(
    Range range,
    TypeDecl memberDecl,
    AstListNode<DeclFragment> fragments
) implements AstNode {
    public Identifier name() {
        return memberDecl.name();
    }
}