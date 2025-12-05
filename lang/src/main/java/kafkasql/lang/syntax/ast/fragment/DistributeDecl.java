package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record DistributeDecl(
    Range range,
    AstListNode<Identifier> keys
) implements DeclFragment { }
