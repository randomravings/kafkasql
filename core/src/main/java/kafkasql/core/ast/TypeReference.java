package kafkasql.core.ast;

public record TypeReference(Range range, QName qName) implements AnyT {}
