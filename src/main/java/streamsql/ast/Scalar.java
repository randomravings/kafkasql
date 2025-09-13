package streamsql.ast;

import java.util.Optional;

public record Scalar(QName qName, PrimitiveType primitive, Optional<Expr> validation, Optional<Literal> defaultValue) implements ComplexType {}
