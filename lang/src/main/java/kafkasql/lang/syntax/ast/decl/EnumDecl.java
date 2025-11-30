package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.literal.EnumLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;

public final record EnumDecl(
    Range range,
    Identifier name,
    TypedOptional<PrimitiveTypeNode> baseType,
    TypedList<EnumSymbolDecl> symbols,
    TypedOptional<EnumLiteralNode> defaultValue,
    TypedOptional<DocNode> doc
) implements TypeDecl { }
