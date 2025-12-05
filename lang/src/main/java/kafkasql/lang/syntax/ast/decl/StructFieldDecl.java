package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstOptionalNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.literal.NullLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final record StructFieldDecl(
    Range range,
    Identifier name,
    TypeNode type,
    AstOptionalNode<NullLiteralNode> nullable,
    AstListNode<DeclFragment> fragments
) implements TypeMemberDecl { }
