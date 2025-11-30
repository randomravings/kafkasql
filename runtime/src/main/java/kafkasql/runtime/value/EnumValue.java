package kafkasql.runtime.value;

import java.util.Objects;

import kafkasql.runtime.type.EnumType;
import kafkasql.runtime.type.EnumTypeSymbol;

/**
 * Runtime value of an ENUM type.
 */
public final class EnumValue implements Value {

    private final EnumType type;
    private final EnumTypeSymbol symbol;

    public EnumValue(EnumType type, EnumTypeSymbol symbol) {
        this.type = Objects.requireNonNull(type, "type");
        this.symbol = Objects.requireNonNull(symbol, "symbol");
    }

    public EnumType type() {
        return type;
    }

    public EnumTypeSymbol symbol() {
        return symbol;
    }

    public String symbolName() {
        return symbol.name();
    }

    public long numericValue() {
        return symbol.value();
    }

    @Override
    public String toString() {
        return type.fqn().toString() + "." + symbol.name();
    }
}
