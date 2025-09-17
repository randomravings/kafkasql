package streamsql.ast;

public sealed interface CreateStmt extends Stmt
    permits CreateContext, CreateType, CreateStream {
    QName qName();
}