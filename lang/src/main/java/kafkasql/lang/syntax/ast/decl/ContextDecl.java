package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record ContextDecl(
    Range range,
    Identifier name,
    TypedOptional<DocNode> doc
) implements Decl
{ }
