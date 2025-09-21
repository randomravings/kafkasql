package kafkasql.core.ast;

import java.util.List;

import kafkasql.core.Range;

public class ReadTypeBlockList extends AstListNode<ReadTypeBlock> {
    public ReadTypeBlockList(Range range) {
        super(range);
    }
    public ReadTypeBlockList(Range range, List<ReadTypeBlock> items) {
        super(range, items);
    }
}
