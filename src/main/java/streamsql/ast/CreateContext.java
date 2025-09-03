package streamsql.ast;

public final record CreateContext(Context context) implements Create {
    public QName qName() { return context.qName(); }
}