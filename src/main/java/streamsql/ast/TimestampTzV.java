package streamsql.ast;

import java.time.ZonedDateTime;

@SuppressWarnings("unchecked")
public final record TimestampTzV(ZonedDateTime value) implements TemporalV {
    public AnyT type() { return TimestampTzT.get(precision()); }
    public Int8V precision() { return new Int8V((byte) Math.ceil(Math.log10(value.getNano()))); }
}
