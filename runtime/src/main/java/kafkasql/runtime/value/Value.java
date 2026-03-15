package kafkasql.runtime.value;

import kafkasql.runtime.type.AnyType;

public sealed interface Value
    permits EnumValue, ScalarValue, StructValue, UnionValue {
    AnyType type();
}
