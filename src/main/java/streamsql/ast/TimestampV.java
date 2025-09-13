package streamsql.ast;

import java.time.LocalDateTime;

@SuppressWarnings("unchecked")
public final record TimestampV(LocalDateTime value) implements TemporalV {
    public Int8V precision() { return new Int8V((byte) Math.ceil(Math.log10(value.getNano()))); }
}
