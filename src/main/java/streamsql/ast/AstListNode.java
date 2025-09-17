package streamsql.ast;

import java.util.ArrayList;
import java.util.List;

public abstract class AstListNode<T extends AstNode> extends ArrayList<T> implements AstNode {
    private final Range range;

    public AstListNode() {
        super();
        this.range = Range.NONE;
    }

    public AstListNode(Range range) {
        super();
        this.range = range;
    }

    public AstListNode(Range range, List<T> items) {
        super(items);
        this.range = range;
    }
    
    public static <T extends AstNode> List<T> of(T item) {
        var list = new ArrayList<T>();
        list.add(item);
        return list;
    }
    
    public Range range() {
        return range;
    }
}
