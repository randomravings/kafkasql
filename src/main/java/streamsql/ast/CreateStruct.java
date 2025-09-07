package streamsql.ast;

public final record CreateStruct(Struct type) implements CreateType {
    public QName qName() { return type.qName(); }
}
