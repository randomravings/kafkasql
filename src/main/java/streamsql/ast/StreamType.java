package streamsql.ast;

public sealed interface StreamType extends AstNode
    permits StreamInlineT, StreamReferenceT {
    Identifier alias();
    AstOptionalNode<DistributeClause> distributeClause();
}
