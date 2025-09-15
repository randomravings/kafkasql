package streamsql.ast;

public final record CreateScalar(ScalarT type) implements CreateType {
    public QName qName() { return type.qName(); }

}
