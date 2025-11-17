package kafkasql.lang.ast;

import java.time.LocalDateTime;

public final record TimestampV(Range range, LocalDateTime value) implements TemporalV, Comparable<TimestampV> {
    public byte precision() { return (byte) Math.log10(value.getNano()); }
    public int compareTo(TimestampV o) {
        return this.value.compareTo(o.value);
    }
}
