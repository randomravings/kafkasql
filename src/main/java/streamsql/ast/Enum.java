package streamsql.ast;

import java.util.List;
import java.util.Optional;

public record Enum(QName qName, IntegerT type, BoolV isMask, List<EnumSymbol> symbols, Optional<Identifier> defaultSymbol) implements ComplexType {}
