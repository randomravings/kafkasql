package streamsql.ast;

public sealed interface CompositeType extends DataType permits Composite.List, Composite.Map {

}
