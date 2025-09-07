package streamsql.ast;

public sealed interface ComplexType extends DataType permits
    Enum, Scalar, Struct, Union {
        QName qName();
}
