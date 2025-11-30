package kafkasql.lang.semantic.factory;

import kafkasql.runtime.type.*;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;
import kafkasql.lang.syntax.ast.type.CompositeTypeNode;
import kafkasql.lang.syntax.ast.type.PrimitiveTypeNode;
import kafkasql.lang.syntax.ast.type.TypeNode;

/**
 * 🤙 TypeFactory
 *
 * Super simple router from AST {@link TypeNode} to runtime {@link AnyType}.
 *
 * - PrimitiveTypeNode  -> PrimitiveTypeFactory
 * - CompositeTypeNode  -> CompositeTypeFactory
 * - TypeRefNode        -> TypeReference (by FQN)
 *
 * No defaults, no mystery branches — if you add a new TypeNode variant,
 * the compiler will scream at you until you handle it here.
 */
public final class TypeFactory {

    private TypeFactory() {
        // bro, no instances
    }

    /**
     * Convert an AST {@link TypeNode} (from the grammar) into a runtime {@link AnyType}.
     *
     * This is for *type usages* (fields, members, parameters), not declarations.
     * Complex type declarations (struct/enum/scalar/union) are handled by
     * {@link ComplexTypeFactory}.
     */
    public static AnyType fromAst(TypeNode node) {
        return switch (node) {

            // primitive: BOOL, INT8, STRING, DECIMAL(10,2), DATE, etc.
            case PrimitiveTypeNode p  ->
                PrimitiveTypeFactory.fromAst(p);

            // composite: LIST<T>, MAP<K,V>
            case CompositeTypeNode c  ->
                CompositeTypeFactory.fromAst(c);

            // reference to a named type: STRUCT, ENUM, SCALAR, UNION, etc.
            case ComplexTypeNode ref ->
                TypeReference.get(ref.name().fullName());
        };
    }
}