package streamsql.ast;

public final record CreateStruct(StructT type) implements CreateType {
    public QName qName() { return type.qName(); }
}
