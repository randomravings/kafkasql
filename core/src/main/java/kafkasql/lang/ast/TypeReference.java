package kafkasql.lang.ast;

public record TypeReference(Range range, QName qName) implements AnyT {}
