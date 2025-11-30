package kafkasql.lang.syntax.ast.misc;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

public final record Identifier(
    Range range,
    String name
) implements AstNode {
    public boolean isEmpty() {
        return name == null || name.isEmpty();
    }
};

