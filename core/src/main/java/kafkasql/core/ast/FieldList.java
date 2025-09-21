package kafkasql.core.ast;

import kafkasql.core.Range;

public class FieldList extends AstListNode<Field> {
    public FieldList(Range range) {
        super(range);
    }
}
