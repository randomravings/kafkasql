package streamsql.ast;

import java.util.List;

public final record ProjectionList(List<ProjectionExpr> items) implements Projection {}
