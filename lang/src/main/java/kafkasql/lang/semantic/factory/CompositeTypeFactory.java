package kafkasql.lang.semantic.factory;

import kafkasql.runtime.type.*;
import kafkasql.lang.syntax.ast.type.CompositeTypeNode;
import kafkasql.lang.syntax.ast.type.ListTypeNode;
import kafkasql.lang.syntax.ast.type.MapTypeNode;

public final class CompositeTypeFactory {

    private CompositeTypeFactory() {}

    public static CompositeType fromAst(CompositeTypeNode ast) {
        return switch (ast) {
            
            case ListTypeNode l ->
                new ListType(TypeFactory.fromAst(l.elementType()));

            case MapTypeNode m ->
                new MapType(
                    PrimitiveTypeFactory.fromAst(m.keyType()),
                    TypeFactory.fromAst(m.valueType())
                );
        };
    }
}