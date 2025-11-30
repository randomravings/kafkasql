package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record DistributeNode(
    Range range,
    TypedList<Identifier> keys
) implements AstNode { }
