package streamsql.ast;

public record TypeReference(Range range, QName qName) implements AnyT {}
