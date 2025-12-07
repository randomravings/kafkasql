package kafkasql.lang.syntax.ast.decl;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;

public record StructDecl(
    Range range,
    AstListNode<StructFieldDecl> fields
) implements TypeKindDecl { }
