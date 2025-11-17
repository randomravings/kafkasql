package kafkasql.lang.ast;

import java.time.ZonedDateTime;

public final record TimestampTzV(Range range, ZonedDateTime value) implements TemporalV, Comparable<TimestampTzV> {
    public byte precision() { return (byte) Math.log10(value.getNano()); }
    @Override
    public int compareTo(TimestampTzV o) {
        return this.value.compareTo(o.value);
    }
}
