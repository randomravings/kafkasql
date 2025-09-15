package streamsql.ast;

public sealed interface PrimitiveV extends AnyV permits
    BoolV, AlphaV, BinaryV, NumberV, TemporalV {
    <V> V value();
}
