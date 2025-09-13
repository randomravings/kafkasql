package streamsql.ast;

import java.util.Map;

public final record MapV (Map<Literal, AnyV> values) implements AnyV { }
