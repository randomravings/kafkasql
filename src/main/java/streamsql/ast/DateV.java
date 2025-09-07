package streamsql.ast;

import java.time.LocalDate;

public final record DateV(LocalDate value) implements Temporal<DateV, LocalDate> { }
