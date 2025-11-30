package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record StructDecl(
    Range range,
    Identifier name,
    TypedList<StructFieldDecl> fields,
    TypedList<CheckNode> checks,
    TypedOptional<DocNode> doc
) implements TypeDecl { }
