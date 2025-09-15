package streamsql.ast;

import java.util.Map;

public final record StructV(Map<Identifier, AnyV> values) implements ComplexV { }
