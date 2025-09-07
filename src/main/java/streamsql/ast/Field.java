package streamsql.ast;

public final record Field(Identifier name, DataType typ, boolean optional, String defaultJson) {}