package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.fragment.ProjectionNode;
import kafkasql.lang.syntax.ast.fragment.WhereNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record ReadTypeBlock(
    Range range,
    Identifier alias,
    ProjectionNode projection,
    TypedOptional<WhereNode> where
) implements AstNode { }
