package kafkasql.runtime.type;

public record ListType(
    AnyType item
) implements CompositeType { }
