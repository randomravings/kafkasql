package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface Stmt
    extends AstNode
    permits UseStmt,
            ShowStmt,
            ExplainStmt,
            CreateStmt,
            ReadStmt,
            WriteStmt
{ }
