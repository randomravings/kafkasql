package kafkasql.runtime.value;

import kafkasql.runtime.type.AnyType;

public sealed interface Value
    permits EnumValue, StructValue, UnionValue {
    AnyType type();
}
