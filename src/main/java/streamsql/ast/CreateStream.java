package streamsql.ast;

public final record CreateStream(DataStream stream) implements CreateStmt {
    public QName qName() { return stream.qName(); }
}
