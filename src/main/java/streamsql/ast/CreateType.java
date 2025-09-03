package streamsql.ast;

public final record CreateType(ComplexType complexType) implements Create {
    public QName qName() { return complexType.qName(); }
}
