package kafkasql.core.ast;

public final record UnionV(Range range, QName unionName, Identifier unionMemberName, AnyV value) implements ComplexV {

}
