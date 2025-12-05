package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.constExpr.ConstExpr;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record EnumSymbolDecl(
    Range range,
    Identifier name,
    ConstExpr value,
    AstListNode<DeclFragment> fragments
) implements TypeMemberDecl { }
