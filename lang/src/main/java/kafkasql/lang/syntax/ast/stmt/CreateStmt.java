package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record CreateStmt(
    Range range,
    Decl decl
) implements Stmt
{
    Identifier name() {
        return decl.name();
    }
}
