package streamsql.ast;

import java.time.LocalTime;

public final record TimeV(Range range, LocalTime value) implements TemporalV { }
