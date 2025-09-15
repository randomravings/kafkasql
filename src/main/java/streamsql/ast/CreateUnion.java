package streamsql.ast;

public final record CreateUnion(UnionT type) implements CreateType {
    public QName qName() { return type.qName(); }
}
