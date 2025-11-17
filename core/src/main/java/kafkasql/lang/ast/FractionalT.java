package kafkasql.lang.ast;

public sealed interface FractionalT extends NumberT
    permits Float32T, Float64T, DecimalT  {

}
