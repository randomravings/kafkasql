package streamsql.ast;

public sealed interface AlphaV extends Literal permits StringV, CharV, UuidV { }
