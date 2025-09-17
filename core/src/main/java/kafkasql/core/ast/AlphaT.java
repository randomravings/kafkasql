package kafkasql.core.ast;

public sealed interface AlphaT extends PrimitiveT permits StringT, CharT, UuidT { }
