package streamsql.ast;

public final record CreateStream(Range range, StreamT stream) implements CreateStmt {
    public QName qName() { return stream.qName(); }
}
