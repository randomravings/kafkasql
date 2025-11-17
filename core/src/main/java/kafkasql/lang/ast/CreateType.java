package kafkasql.lang.ast;

public final record CreateType(Range range, ComplexT type) implements CreateStmt {
    public QName qName() { return type.qName(); }
}
