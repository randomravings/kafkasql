package kafkasql.lang.ast;

import java.util.List;

public final class ProjectionList extends AstListNode<ProjectionExpr> implements Projection {
    public ProjectionList(Range range) {
        super(range);
    }
    public ProjectionList(Range range, List<ProjectionExpr> items) {
        super(range, items);
    }
}
