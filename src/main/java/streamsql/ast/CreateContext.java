package streamsql.ast;

public final record CreateContext(Context context) implements CreateStmt {
    public QName qName() { return context.qName(); }
}