package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Field(Range range, Identifier name, AnyT type, AstOptionalNode<NullV> nullable, AstOptionalNode<AnyV> defaultValue) implements AstNode {}