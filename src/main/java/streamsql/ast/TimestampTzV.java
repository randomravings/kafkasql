package streamsql.ast;

import java.time.ZonedDateTime;

public final record TimestampTzV(ZonedDateTime value) implements Temporal<TimestampTzV, ZonedDateTime> { }
