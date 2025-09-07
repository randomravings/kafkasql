package streamsql.ast;

import java.time.LocalDateTime;

public final record TimestampV(LocalDateTime value) implements Temporal<TimestampV, LocalDateTime> { }
