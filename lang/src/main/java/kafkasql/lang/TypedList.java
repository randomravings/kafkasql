package kafkasql.lang;

import java.util.ArrayList;

import kafkasql.lang.syntax.ast.AstNode;

public final class TypedList<T extends AstNode>
    extends ArrayList<T> {
    
    private final Class<T> _itemType;
    public TypedList(Class<T> itemType) {
        super();
        this._itemType = itemType;
    }
    public Class<T> getItemType() {
        return _itemType;
    }
}
