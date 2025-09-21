package kafkasql.core.ast;

import kafkasql.core.Range;

public class EnumSymbolList extends AstListNode<EnumSymbol> {
    public EnumSymbolList(Range range) {
        super(range);
    }
}
