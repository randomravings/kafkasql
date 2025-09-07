package streamsql.ast;

public sealed interface Temporal<T extends Temporal<T, V>, V> extends Literal<T, V> permits DateV, TimeV, TimestampV, TimestampTzV {
    V value();
}
