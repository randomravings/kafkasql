package kafkasql.core.ast;

import kafkasql.core.Range;

public record TypeReference(Range range, QName qName) implements AnyT {}
