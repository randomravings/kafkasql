package streamsql.ast;

public sealed interface AlphaT extends PrimitiveType permits StringT, CharT, UuidT { }
