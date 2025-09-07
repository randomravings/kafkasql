package streamsql.ast;

import java.util.List;

public record Enum(QName qName, List<EnumSymbol> symbols) implements ComplexType {}
