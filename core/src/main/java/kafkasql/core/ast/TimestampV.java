package kafkasql.core.ast;

import java.time.LocalDateTime;

import kafkasql.core.Range;

public final record TimestampV(Range range, LocalDateTime value) implements TemporalV {
    public byte precision() { return (byte) Math.log10(value.getNano()); }
}
