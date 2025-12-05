package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;

public final record DerivedTypeDecl(
    Range range,
    ComplexTypeNode target
) implements TypeKindDecl { }
