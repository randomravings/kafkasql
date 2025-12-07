package kafkasql.lang.syntax.ast.decl;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record TypeDecl(
    Range range,
    Identifier name,
    TypeKindDecl kind,
    AstListNode<DeclFragment> fragments
) implements Decl { }
