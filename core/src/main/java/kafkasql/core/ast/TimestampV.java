package kafkasql.core.ast;

import java.time.LocalDateTime;

import kafkasql.core.Range;

public final record TimestampV(Range range, LocalDateTime value) implements TemporalV { }
