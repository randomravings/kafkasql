package kafkasql.runtime.type;

import java.util.Optional;
import kafkasql.runtime.Name;

public sealed interface ComplexType extends AnyType permits
    EnumType, ScalarType, StructType, UnionType {
    Name fqn();
    Optional<String> doc();
}
