package streamsql.ast;

import java.time.LocalTime;

@SuppressWarnings("unchecked")
public final record TimeV(LocalTime value) implements TemporalV {
    public Int8V precision() { return new Int8V((byte) Math.ceil(Math.log10(value.getNano()))); }
}
