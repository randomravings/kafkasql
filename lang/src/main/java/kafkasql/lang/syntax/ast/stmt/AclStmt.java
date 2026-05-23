package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.runtime.diagnostics.Range;

public sealed interface AclStmt extends Stmt
    permits AclStmt.Grant, AclStmt.Revoke {

    enum Privilege { READ, WRITE, CREATE, MODIFY, ALL }
    enum Target    { STREAM, CONTEXT }

    record Grant(Range range, Privilege privilege, Target target, QName resource, String principal)
        implements AclStmt {}

    record Revoke(Range range, Privilege privilege, Target target, QName resource, String principal)
        implements AclStmt {}
}
