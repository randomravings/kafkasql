package streamsql.ast;

public sealed interface ComplexType extends DataType permits
    Complex.Enum, Complex.Scalar, Complex.Struct, Complex.Union {
        QName qName();
}
