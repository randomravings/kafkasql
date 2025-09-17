package streamsql.ast;

public final record DecimalT(Range range, byte precision, byte scale) implements FractionalT { }