package kafkasql.core.ast;

public record ReadTypeBlock(Range range, Identifier alias, Projection projection, AstOptionalNode<WhereClause> where) implements AstNode { }
