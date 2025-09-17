package streamsql.ast;

import java.util.List;

public class ReadTypeBlockList extends AstListNode<ReadTypeBlock> {
    public ReadTypeBlockList(Range range) {
        super(range);
    }
    public ReadTypeBlockList(Range range, List<ReadTypeBlock> items) {
        super(range, items);
    }
}
