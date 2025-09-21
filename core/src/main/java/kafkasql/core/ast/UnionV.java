package kafkasql.core.ast;

import kafkasql.core.Range;

public final record UnionV(Range range, QName unionName, Identifier unionMemberName, AnyV value) implements ComplexV {

}
