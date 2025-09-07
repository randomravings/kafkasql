package streamsql.ast;

public final record CreateScalar(Scalar type) implements CreateType {
    public QName qName() { return type.qName(); }

}
