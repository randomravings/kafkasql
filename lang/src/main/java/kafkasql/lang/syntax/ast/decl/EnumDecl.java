package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstOptionalNode;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final record EnumDecl(
    Range range,
    AstOptionalNode<TypeNode> type,
    AstListNode<EnumSymbolDecl> symbols
) implements TypeKindDecl { }
