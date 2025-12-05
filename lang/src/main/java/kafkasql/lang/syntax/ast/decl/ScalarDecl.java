package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final record ScalarDecl(
    Range range,
    TypeNode type
) implements TypeKindDecl { }
