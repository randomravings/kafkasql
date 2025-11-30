package kafkasql.runtime.value;

import java.util.Objects;

import kafkasql.runtime.type.UnionType;

/**
 * Runtime value instance of a UNION type.
 */
public final class UnionValue implements Value {

    private final UnionType type;
    private final String memberName;
    private final Object value; // canonical runtime value for that member

    public UnionValue(UnionType type, String memberName, Object value) {
        this.type = Objects.requireNonNull(type, "type");
        this.memberName = Objects.requireNonNull(memberName, "memberName");
        this.value = value; // may be null if member type allows it
    }

    public UnionType type() {
        return type;
    }

    public String memberName() {
        return memberName;
    }

    public Object value() {
        return value;
    }

    @Override
    public String toString() {
        return "UnionValue(" + type.fqn().toString() + "." + memberName + "=" + value + ")";
    }
}
