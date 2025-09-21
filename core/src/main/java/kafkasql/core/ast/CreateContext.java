package kafkasql.core.ast;

import kafkasql.core.Range;

public final record CreateContext(Range range, Context context) implements CreateStmt {
    public QName qName() { return context.qName(); }
}