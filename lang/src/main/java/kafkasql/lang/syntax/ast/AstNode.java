package kafkasql.lang.syntax.ast;

import kafkasql.runtime.diagnostics.Range;

public interface AstNode {
    Range range();
}
