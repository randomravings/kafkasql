package kafkasql.lang.ast;

import java.time.LocalDate;

public final record DateV(Range range, LocalDate value) implements TemporalV, Comparable<DateV> {
    @Override
    public int compareTo(DateV o) {
        return this.value.compareTo(o.value);
    }
}
