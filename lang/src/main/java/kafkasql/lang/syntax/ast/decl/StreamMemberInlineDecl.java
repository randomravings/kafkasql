package kafkasql.lang.syntax.ast.decl;

import kafkasql.lang.TypedList;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.DistributeNode;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record StreamMemberInlineDecl(
    Range range,
    Identifier name,
    TypedList<StructFieldDecl> fields,
    TypedOptional<DistributeNode> distribute,
    TypedOptional<Identifier> timestampField,
    TypedList<CheckNode> checks,
    TypedOptional<DocNode> doc
) implements StreamMemberDecl { }
