package kafkasql.core.ast;

import kafkasql.core.Range;

public record ReadTypeBlock(Range range, Identifier alias, Projection projection, AstOptionalNode<WhereClause> where) implements AstNode { }
