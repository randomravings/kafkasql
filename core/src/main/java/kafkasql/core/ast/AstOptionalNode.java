package kafkasql.core.ast;

import java.util.Optional;

import kafkasql.core.Range;

public final class AstOptionalNode<T extends AstNode>  implements AstNode {
    private final T value;
    private final Range range;

    private AstOptionalNode(Range range, T value) {
        this.range = range;
        this.value = value;
    }

    public static <T extends AstNode> AstOptionalNode<T> of(T value) {
        return new AstOptionalNode<>(value.range(), value);
    }
    
    public static <T extends AstNode> AstOptionalNode<T> empty() {
        return new AstOptionalNode<>(Range.NONE, null);
    }
    
    public boolean isPresent() {
        return value != null;
    }

    public boolean isEmpty() {
        return value == null;
    }

    public T get() {
        if (value == null)
            throw new IllegalStateException("No value present");
        return value;
    }
    
    public Optional<T> toOptional() {
        return Optional.ofNullable(value);
    }
    
    @Override
    public Range range() {
        return value != null ? range : Range.NONE;
    }
}