package kafkasql.core.ast;

public sealed interface NumberT extends PrimitiveT
    permits IntegerT, FractionalT {  }
