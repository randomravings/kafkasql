package streamsql.ast;

public sealed interface Create extends DdlStmt
  permits CreateContext, CreateType, CreateStream {
    QName qName();
  }