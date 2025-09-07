package streamsql.ast;

import java.time.LocalTime;

public final record TimeV(LocalTime value) implements Temporal<TimeV, LocalTime> { }
