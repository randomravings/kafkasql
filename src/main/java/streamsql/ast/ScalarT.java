package streamsql.ast;

import java.util.Optional;

public record ScalarT(QName qName, PrimitiveT primitive, Optional<Expr> validation, Optional<PrimitiveV> defaultValue) implements ComplexT {}
