package streamsql.ast;

public sealed interface CreateStmt extends DdlStmt
  permits CreateContext, CreateType, CreateStream {
    QName qName();
  }