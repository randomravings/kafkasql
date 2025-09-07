package streamsql.ast;

public sealed interface Literal<T extends Literal<T, V>, V> extends Expr permits
    NullV, BoolV, EnumV,
    Numeric, Chars, Temporal, UuidV, Identifier, Path {
}
