package streamsql.ast;

public sealed interface NumberT extends PrimitiveType
    permits IntegerT, FractionalT {  }
