package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.syntax.ast.show.ShowTarget;

/**
 * Base sealed interface for all SHOW statements.
 */
public sealed interface ShowStmt extends Stmt 
    permits ShowCurrentStmt, ShowAllStmt, ShowContextualStmt {
    
    ShowTarget target();
}

