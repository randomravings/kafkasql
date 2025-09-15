package streamsql.ast;

public sealed interface AlphaT extends PrimitiveT permits StringT, CharT, UuidT { }
