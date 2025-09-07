package streamsql.ast;

import java.util.List;

public final record StreamInlineT(List<Field> fields, Identifier alias, List<Identifier> distributionKeys) implements StreamType {}
