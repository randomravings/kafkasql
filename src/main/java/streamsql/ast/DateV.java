package streamsql.ast;

import java.time.LocalDate;

@SuppressWarnings("unchecked")
public final record DateV(LocalDate value) implements TemporalV {
    public AnyT type() { return DateT.get(); }
}
