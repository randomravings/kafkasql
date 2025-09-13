package streamsql.ast;

public sealed interface ComplexType extends AnyT permits
    Enum, Scalar, Struct, Union {
        QName qName();
}
