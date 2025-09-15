package streamsql.ast;

import java.util.Optional;

public final record ProjectionExpr(Expr expr, Optional<Identifier> alias) {}
