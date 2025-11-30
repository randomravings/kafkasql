package kafkasql.lang.semantic.factory;

import kafkasql.runtime.Name;
import kafkasql.runtime.type.*;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.lang.syntax.ast.fragment.DocNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🧘‍♂️ ComplexTypeFactory
 * 
 * This bro builds *runtime-level* complex types from AST-level type declarations.
 * 
 * No side effects.
 * No sneaky caches.
 * Only the pure, radiant, L1-cache-aligned essence of types.
 * 
 * Used by:
 *   - Semantic DefaultBinder
 *   - Semantic Stream Binder
 *   - Runtime schema construction
 */
public final class ComplexTypeFactory {

    private ComplexTypeFactory() { }

    // ========================================================================
    // STRUCT
    // ========================================================================

    public static StructType fromStructDecl(StructDecl decl, Name declName, Diagnostics diags) {

        LinkedHashMap<String, StructTypeField> fields =
            decl.fields().stream()
                .map(ComplexTypeFactory::structFieldFromAst)
                .collect(Collectors.toMap(
                    StructTypeField::name,
                    f -> f,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));

        return new StructType(
            declName,
            fields,
            extractDoc(decl.doc())
        );
    }

    private static StructTypeField structFieldFromAst(StructFieldDecl f) {

        AnyType runtimeType = TypeFactory.fromAst(f.type());

        Optional<Object> defaultValue =
            f.defaultValue().map(dv -> LiteralValueFactory.evaluate(dv));

        return new StructTypeField(
            f.name().name(),
            runtimeType,
            f.nullable(),
            defaultValue,
            extractDoc(f.doc())
        );
    }

    // ========================================================================
    // ENUM
    // ========================================================================

    public static EnumType fromEnumDecl(EnumDecl decl, Name declName, Diagnostics diags) {

        PrimitiveType base = PrimitiveType.int32();
        if(decl.baseType().isPresent())
            base = PrimitiveTypeFactory.fromAst(decl.baseType().get());

        if (!base.isIntegerKind()) {
            diags.error(
                decl.baseType().map(t -> t.range()).orElse(decl.range()),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_ENUM_BASE_TYPE,
                "Enum base type must be an integral primitive type."
            );
        }
        
        List<EnumTypeSymbol> symbols = decl.symbols().stream()
            .map(ComplexTypeFactory::enumSymbolFromAst)
            .toList();

        return new EnumType(
            declName,
            base.kind(),
            symbols,
            extractDoc(decl.doc())
        );
    }

    private static EnumTypeSymbol enumSymbolFromAst(EnumSymbolDecl s) {
        return new EnumTypeSymbol(
            s.name().name(),
            LiteralValueFactory.evaluateAsLong(s.value()),
            extractDoc(s.doc())
        );
    }

    // ========================================================================
    // SCALAR
    // ========================================================================

    public static ScalarType fromScalarDecl(ScalarDecl decl, Name declName) {

        PrimitiveType base = PrimitiveTypeFactory.fromAst(decl.baseType());

        Optional<Object> defaultValue =
            decl.defaultValue().map(LiteralValueFactory::evaluate);

        return new ScalarType(
            declName,
            base,
            defaultValue,
            extractDoc(decl.doc())
        );
    }

    // ========================================================================
    // UNION
    // ========================================================================

    public static UnionType fromUnionDecl(UnionDecl decl, Name declName) {

        LinkedHashMap<String, UnionTypeMember> members =
            decl.members().stream()
                .map(ComplexTypeFactory::unionMemberFromAst)
                .collect(Collectors.toMap(
                    UnionTypeMember::name,
                    u -> u,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));

        return new UnionType(
            declName,
            members,
            extractDoc(decl.doc())
        );
    }

    private static UnionTypeMember unionMemberFromAst(UnionMemberDecl m) {
        AnyType runtimeType = TypeFactory.fromAst(m.type());
        return new UnionTypeMember(
            m.name().name(),
            runtimeType,
            extractDoc(m.doc())
        );
    }

    // ========================================================================
    // DOC HELPER
    // ========================================================================

    private static Optional<String> extractDoc(TypedOptional<DocNode> docNode) {
        if (docNode.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(docNode.get().comment());
        }
    }
}
