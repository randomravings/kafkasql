package streamsql.ast;

public sealed interface Literal extends AnyV permits
    NullV, BoolV, AlphaV, BinaryV, NumberV, TemporalV {
    <V> V value();
}
