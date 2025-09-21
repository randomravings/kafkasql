package kafkasql.core.ast;

import java.time.LocalDate;

import kafkasql.core.Range;

public final record DateV(Range range, LocalDate value) implements TemporalV { }
