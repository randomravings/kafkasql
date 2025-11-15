package kafkasql.core.ast;

import java.time.LocalTime;

public final record TimeV(Range range, LocalTime value) implements TemporalV, Comparable<TimeV> {
    public byte precision() { return (byte) Math.log10(value.getNano()); }
    @Override
    public int compareTo(TimeV o) {
        return this.value.compareTo(o.value);
    }
}
