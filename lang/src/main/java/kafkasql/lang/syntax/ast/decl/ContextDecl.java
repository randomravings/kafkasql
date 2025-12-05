package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record ContextDecl(
    Range range,
    Identifier name,
    AstListNode<DeclFragment> fragments
) implements Decl
{ }
