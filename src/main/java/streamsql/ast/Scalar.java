package streamsql.ast;

public record Scalar(QName qName, PrimitiveType primitive) implements ComplexType {}
