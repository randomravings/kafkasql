package kafkasql.core.ast;

import java.time.LocalTime;

import kafkasql.core.Range;

public final record TimeV(Range range, LocalTime value) implements TemporalV { }
