package streamsql.ast;

public final record Field(Range range, Identifier name, AnyT type, AstOptionalNode<NullV> nullable, AstOptionalNode<AnyV> defaultValue) implements AstNode {}