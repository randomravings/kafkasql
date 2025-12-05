package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface DeclFragment
    extends AstNode
    permits CheckNode,
            DefaultNode,
            DocNode,
            ConstraintNode,
            DistributeDecl,
            TimestampDecl
{ }
