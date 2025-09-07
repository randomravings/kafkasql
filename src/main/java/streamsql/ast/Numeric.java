package streamsql.ast;

public sealed interface Numeric<T extends Numeric<T, V>, V> extends Literal<T, V> permits
    Int8V, UInt8V, Int16V, UInt16V,
    Int32V, UInt32V, Int64V, UInt64V,
    Float32V, Float64V, DecimalV {
    public T add(T other);
    public T subtract(T other);
    public T multiply(T other);
    public T divide(T other);
}
