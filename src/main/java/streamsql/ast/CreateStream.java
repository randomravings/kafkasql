package streamsql.ast;

public final record CreateStream(StreamType stream) implements Create {
    public QName qName() { return stream.qName(); }
}
