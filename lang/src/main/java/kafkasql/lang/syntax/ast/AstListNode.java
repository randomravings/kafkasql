package kafkasql.lang.syntax.ast;

import java.util.ArrayList;

import kafkasql.lang.diagnostics.Range;

public final class AstListNode<T extends AstNode>
    extends ArrayList<T>
    implements AstNode {

    private Class<T> _clazz;

    public AstListNode(Class<T> clazz) {
        super();
    }
        
    public Range range() {
        if (isEmpty())
            return Range.NONE;
        return
            Range.merge(getFirst().range(), this.getLast().range());
    }

    public Class<T> clazz() {
        return _clazz;
    }
}
