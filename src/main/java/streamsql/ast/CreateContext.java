package streamsql.ast;

public final record CreateContext(Range range, Context context) implements CreateStmt {
    public QName qName() { return context.qName(); }
}