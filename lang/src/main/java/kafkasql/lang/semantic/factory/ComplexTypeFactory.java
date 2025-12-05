package kafkasql.lang.semantic.factory;

import kafkasql.runtime.Name;
import kafkasql.runtime.type.*;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.util.FragmentUtils;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;

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

    public static ComplexType fromTypeDecl(Name name, TypeDecl decl, Diagnostics diags) {
        return switch (decl.kind()) {
            case StructDecl s ->
                fromStructDecl(name, s, decl.fragments(), diags);
            case EnumDecl e ->
                fromEnumDecl(name, e, decl.fragments(), diags);
            case ScalarDecl s ->
                fromScalarDecl(name, s, decl.fragments(), diags);
            case UnionDecl u ->
                fromUnionDecl(name, u, decl.fragments(), diags);
            case DerivedTypeDecl d ->
                throw new IllegalArgumentException("DerivedTypeDecl should be resolved to actual type before calling fromTypeDecl");
        };
    }

    // ========================================================================
    // STRUCT
    // ========================================================================

    public static StructType fromStructDecl(
        Name name,
        StructDecl decl,
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        LinkedHashMap<String, StructTypeField> fields =
            decl.fields().stream()
                .map(f -> structFieldFromAst(f, diags))
                .collect(Collectors.toMap(
                    StructTypeField::name,
                    f -> f,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));

        return new StructType(
            name,
            fields,
            List.of(),  // TODO: Extract CHECK constraints
            FragmentUtils.extractDoc(fragments, diags)
        );
    }

    private static StructTypeField structFieldFromAst(StructFieldDecl f, Diagnostics diags) {

        AnyType runtimeType = TypeFactory.fromAst(f.type());
        boolean nullable = f.nullable().isPresent();
        Optional<String> doc = FragmentUtils.extractDoc(f.fragments(), diags);
        Optional<Object> defaultValue = FragmentUtils.extractDefault(f.fragments(), diags);

        return new StructTypeField(
            f.name().name(),
            runtimeType,
            nullable,
            defaultValue,
            doc
        );
    }

    // ========================================================================
    // ENUM
    // ========================================================================

    public static EnumType fromEnumDecl(
        Name name,
        EnumDecl decl,
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        PrimitiveType base = PrimitiveType.int32();
        if(decl.type().isPresent()) {
            AnyType declaredType = TypeFactory.fromAst(decl.type().get());
            if (!(declaredType instanceof PrimitiveType pt)) {
                diags.error(
                    decl.type().get().range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_ENUM_BASE_TYPE,
                    "Enum base type must be a primitive type."
                );
            } else {
                base = pt;
            }
        }

        List<EnumTypeSymbol> symbols = decl.symbols().stream()
            .map(f -> enumSymbolFromAst(f, diags))
            .toList();
        
        Optional<String> doc = FragmentUtils.extractDoc(fragments, diags);

        return new EnumType(
            name,
            base,
            symbols,
            doc
        );
    }

    private static EnumTypeSymbol enumSymbolFromAst(
        EnumSymbolDecl s,
        Diagnostics diags
    ) {
        Optional<String> doc = FragmentUtils.extractDoc(s.fragments(), diags);
        
        // Evaluate the ConstExpr to get the enum symbol value
        long value;
        if (s.value() instanceof kafkasql.lang.syntax.ast.constExpr.ConstLiteralExpr lit) {
            try {
                value = Long.parseLong(lit.text());
            } catch (NumberFormatException e) {
                // Should never happen if parser is correct
                throw new IllegalArgumentException("Invalid enum symbol value: " + lit.text(), e);
            }
        } else {
            // For now, only support literal values
            // TODO: Support const expressions and symbol references
            throw new UnsupportedOperationException(
                "Enum symbol values must be numeric literals for now. Got: " + s.value().getClass().getSimpleName()
            );
        }
        
        return new EnumTypeSymbol(
            s.name().name(),
            value,
            doc
        );
    }

    // ========================================================================
    // SCALAR
    // ========================================================================

    public static ScalarType fromScalarDecl(
        Name name,
        ScalarDecl decl,
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {
        PrimitiveType base = null;
        AnyType declaredType = TypeFactory.fromAst(decl.type());
        if (!(declaredType instanceof PrimitiveType pt)) {
            diags.error(
                decl.type().range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_SCALAR_BASE_TYPE,
                "Scalar type must be a primitive type."
            );
            base = PrimitiveType.string(); // Fallback to string to continue
        } else {
            base = pt;
        }

        Optional<String> doc = FragmentUtils.extractDoc(fragments, diags);
        Optional<Object> defaultValue = FragmentUtils.extractDefault(fragments, diags);

        return new ScalarType(
            name,
            base,
            defaultValue,
            Optional.empty(),  // TODO: Extract CHECK constraint
            doc
        );
    }

    // ========================================================================
    // UNION
    // ========================================================================

    public static UnionType fromUnionDecl(
        Name name,
        UnionDecl decl,
        AstListNode<DeclFragment> fragments,
        Diagnostics diags
    ) {

        LinkedHashMap<String, UnionTypeMember> members =
            decl.members().stream()
                .map(f -> unionMemberFromAst(f, diags))
                .collect(Collectors.toMap(
                    UnionTypeMember::name,
                    u -> u,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));
        Optional<String> doc = FragmentUtils.extractDoc(fragments, diags);

        return new UnionType(
            name,
            members,
            doc
        );
    }

    private static UnionTypeMember unionMemberFromAst(
        UnionMemberDecl m,
        Diagnostics diags
    ) {
        AnyType runtimeType = TypeFactory.fromAst(m.type());
        Optional<String> doc = FragmentUtils.extractDoc(m.fragments(), diags);
        return new UnionTypeMember(
            m.name().name(),
            runtimeType,
            doc
        );
    }
}
