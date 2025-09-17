package kafkasql.core.ast;

public sealed interface ComplexT extends AnyT permits
    EnumT, ScalarT, StructT, UnionT {
    QName qName();
}
