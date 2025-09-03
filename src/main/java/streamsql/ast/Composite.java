package streamsql.ast;

public class Composite {
    public record List(DataType item) implements CompositeType {}
    public record Map(PrimitiveType key, DataType value) implements CompositeType {}

}
