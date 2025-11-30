package kafkasql.lang;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import kafkasql.lang.syntax.ast.AstNode;

public final class TypedOptional<T extends AstNode> {
    private final Optional<T> _optional;
    private final Class<T> _valueType;
    public TypedOptional(Optional<T> optional, Class<T> valueType) {
        this._optional = optional;
        this._valueType = valueType;
    }
    public Class<T> type() {
        return _valueType;
    }
    public boolean isEmpty() {
        return _optional.isEmpty();
    }
    public T get() {
        return _optional.get();
    }
    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return _optional.map(mapper);
    }
    public boolean isPresent() {
        return _optional.isPresent();
    }
    public void ifPresent(Consumer<? super T> action) {
        _optional.ifPresent(action);
    }
    public static <T extends AstNode> TypedOptional<T> of(T value, Class<T> valueType) {
        return new TypedOptional<>(Optional.of(value), valueType);
    }
    public static <T extends AstNode> TypedOptional<T> empty(Class<T> valueType) {
        return new TypedOptional<>(Optional.empty(), valueType);
    }
}
