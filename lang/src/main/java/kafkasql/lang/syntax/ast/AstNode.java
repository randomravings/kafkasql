package kafkasql.lang.syntax.ast;

import kafkasql.lang.diagnostics.Range;

public interface AstNode {
    Range range();
}
