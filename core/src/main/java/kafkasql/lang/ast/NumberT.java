package kafkasql.lang.ast;

public sealed interface NumberT extends PrimitiveT
    permits IntegerT, FractionalT {  }
