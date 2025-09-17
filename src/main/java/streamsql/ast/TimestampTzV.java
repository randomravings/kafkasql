package streamsql.ast;

import java.time.ZonedDateTime;

public final record TimestampTzV(Range range, ZonedDateTime value) implements TemporalV { }
