package kafkasql.lang.ast;

public record ReadTypeBlock(Range range, Identifier alias, Projection projection, AstOptionalNode<WhereClause> where) implements AstNode { }
