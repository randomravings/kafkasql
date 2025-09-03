package streamsql.ast;

import java.util.List;

public class Complex {

    public record Scalar(QName qName, PrimitiveType primitive) implements ComplexType {}

    public record Enum(QName qName, List<EnumSymbol> symbols) implements ComplexType {}
    public record EnumSymbol(String name, long value) {}

    public record Struct(QName qName, List<StructField> fields) implements ComplexType {}
    public record StructField(String name, DataType typ, boolean optional, String defaultJson) {}

    public record Union(QName qName, List<UnionAlt> types) implements ComplexType {}
    public record UnionAlt(String name, DataType typ) {}
}
