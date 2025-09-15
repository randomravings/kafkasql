package streamsql.ast;

import java.util.List;
import java.util.Optional;

public record EnumT(QName qName, IntegerT type, BoolV isMask, List<EnumSymbol> symbols, Optional<Identifier> defaultSymbol) implements ComplexT {}
