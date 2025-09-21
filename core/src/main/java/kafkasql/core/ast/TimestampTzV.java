package kafkasql.core.ast;

import java.time.ZonedDateTime;

import kafkasql.core.Range;

public final record TimestampTzV(Range range, ZonedDateTime value) implements TemporalV { }
