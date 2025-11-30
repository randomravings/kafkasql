package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record StreamDecl(
    Range range,
    Identifier name,
    TypedList<StreamMemberDecl> streamTypes,
    TypedOptional<DocNode> doc
) implements Decl { }
