package kafkasql.lang.syntax.ast.use;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.QName;

public record ContextUse(
    Range range,
    QName qname
) implements UseTarget {}