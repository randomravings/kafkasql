package kafkasql.core.ast;

public sealed interface CompositeV extends AnyV
    permits ListV, MapV { }
