package streamsql.ast;

public sealed interface Chars<T extends Chars<T, V>, V> extends Literal<T, V> permits
    StringV, FStringV, BytesV, FBytesV {
    public T concat(T other);
}
