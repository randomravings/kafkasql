package streamsql.ast;

public sealed interface FractionalV extends NumberV
    permits DecimalV, Float32V, Float64V {}
