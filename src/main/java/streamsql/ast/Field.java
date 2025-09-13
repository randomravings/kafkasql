package streamsql.ast;

import java.util.Optional;

public final record Field(Identifier name, AnyT typ, BoolV optional, Optional<StringV> defaultValue) {}