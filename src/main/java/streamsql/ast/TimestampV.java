package streamsql.ast;

import java.time.LocalDateTime;

public final record TimestampV(Range range, LocalDateTime value) implements TemporalV { }
