package kafkasql.lang.syntax.ast;

import java.util.Optional;

import kafkasql.runtime.diagnostics.Range;

public final class AstOptionalNode<T extends AstNode>
    implements AstNode {

    private final Class<T> _clazz;
    private final Optional<T> _value;

    private AstOptionalNode(T value, Class<T> clazz) {
        _value = Optional.ofNullable(value);
        _clazz = clazz;
    }
    
    public Range range() {
        return _value
            .map(AstNode::range)
            .orElse(Range.NONE);
    }

    public Class<T> clazz() {
        return _clazz;
    }

    public boolean isEmpty() {
        return _value.isEmpty();
    }

    public boolean isPresent() {
        return _value.isPresent();
    }

    public T get() {
        return _value.get();
    }

    public static <T extends AstNode> AstOptionalNode<T> of(
        T value,
        Class<T> clazz
    ) {
        return new AstOptionalNode<>(value, clazz);
    }

    public static <T extends AstNode> AstOptionalNode<T> empty(
        Class<T> clazz
    ) {
        return new AstOptionalNode<>(null, clazz);
    }
}
