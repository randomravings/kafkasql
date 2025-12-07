package kafkasql.lang.syntax.ast.decl;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.type.TypeNode;

public final record ScalarDecl(
    Range range,
    TypeNode type
) implements TypeKindDecl { }
