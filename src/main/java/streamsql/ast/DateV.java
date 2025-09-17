package streamsql.ast;

import java.time.LocalDate;

public final record DateV(Range range, LocalDate value) implements TemporalV { }
