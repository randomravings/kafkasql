package kafkasql.lang.syntax.ast.use;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface UseTarget
    extends AstNode
    permits ContextUse
{ }
