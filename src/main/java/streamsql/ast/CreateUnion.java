package streamsql.ast;

public final record CreateUnion(Union type) implements CreateType {
    public QName qName() { return type.qName(); }
}
