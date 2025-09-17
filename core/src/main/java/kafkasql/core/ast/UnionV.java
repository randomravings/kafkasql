package kafkasql.core.ast;

public final record UnionV(Range range, Identifier name, AnyV value) implements ComplexV {

}
