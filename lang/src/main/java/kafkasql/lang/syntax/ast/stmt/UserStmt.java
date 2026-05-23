package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import java.util.Optional;

public sealed interface UserStmt extends Stmt
    permits UserStmt.CreateUser, UserStmt.AlterUser, UserStmt.DropUser {

    record CreateUser(Range range, String username, Optional<String> password) implements UserStmt {}
    record AlterUser(Range range, String username, String password) implements UserStmt {}
    record DropUser(Range range, String username) implements UserStmt {}
}
