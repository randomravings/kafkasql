package kafkasql.lang.semantic.bind;

import kafkasql.runtime.type.*;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.factory.ComplexTypeFactory;
import kafkasql.lang.semantic.factory.PrimitiveTypeFactory;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.literal.EnumLiteralNode;
import kafkasql.lang.syntax.ast.literal.LiteralNode;
import kafkasql.lang.syntax.ast.literal.UnionLiteralNode;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;

/**
 * DefaultBinder
 *
 * Binds *default values* for:
 *   - SCALAR types
 *   - STRUCT fields
 *   - ENUM types (default symbol)
 *   - UNION types (default member literal)
 *
 * It uses the typed literal binder so that defaults become fully
 * type-checked, runtime-ready values.
 */
public final class DefaultBinder {

    private DefaultBinder() { }

    public static void bindDefaults(
        Script script,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        for (Stmt stmt : script.statements()) {
            if (!(stmt instanceof CreateStmt ct))
                continue;

            if (!(ct.decl() instanceof TypeDecl decl))
                continue;

            switch (decl) {
                case ScalarDecl s -> bindScalarDefault(s, diags, bindings);
                case StructDecl s -> bindStructDefaults(s, symbols, diags, bindings);
                case EnumDecl   e -> bindEnumDefault(e, symbols, diags, bindings);
                case UnionDecl  u -> bindUnionDefault(u, symbols, diags, bindings);
            }
        }
    }

    // ─────────────────────────────────────────────
    // 1. Scalar default
    // ─────────────────────────────────────────────

    private static void bindScalarDefault(
        ScalarDecl scalar,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        if (scalar.defaultValue().isEmpty())
            return;

        LiteralNode lit = scalar.defaultValue().get();

        // Expected runtime type is the primitive base type
        PrimitiveType baseType = PrimitiveTypeFactory.fromAst(scalar.baseType());

        Object value = LiteralBinder.bindLiteralAsType(
            lit,
            baseType,
            diags,
            bindings
        );

        // Bind default value to the scalar declaration itself
        bindings.put(scalar, value);
    }

    // ─────────────────────────────────────────────
    // 2. Struct field defaults
    // ─────────────────────────────────────────────

    private static void bindStructDefaults(
        StructDecl struct,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        // Get the pre-built runtime type from TypeBuilder
        StructType structType = bindings.getOrNull(struct, StructType.class);
        if (structType == null) {
            // Shouldn't happen if TypeBuilder ran
            return;
        }

        for (StructFieldDecl field : struct.fields()) {

            if (field.defaultValue().isEmpty())
                continue;

            LiteralNode lit = field.defaultValue().get();

            // Get field type from the runtime struct type
            StructTypeField fieldInfo = structType.fields().get(field.name().name());
            if (fieldInfo == null) {
                continue;
            }

            Object value = LiteralBinder.bindLiteralAsType(
                lit,
                fieldInfo.type(),  // This is already a fully-resolved runtime type!
                diags,
                bindings
            );

            bindings.put(field, value);
        }
    }

    // ─────────────────────────────────────────────
    // 3. Enum default
    // ─────────────────────────────────────────────

    private static void bindEnumDefault(
        EnumDecl enumDecl,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        if (enumDecl.defaultValue().isEmpty()) {
            return;
        }

        EnumLiteralNode lit = enumDecl.defaultValue().get();

        // Get the Name from symbols
        kafkasql.runtime.Name enumName = symbols.nameOf(enumDecl).orElseThrow(() ->
            new IllegalStateException("EnumDecl not in symbol table")
        );

        // Build the runtime EnumType from the AST declaration
        EnumType runtimeEnum = ComplexTypeFactory.fromEnumDecl(enumDecl, enumName, diags);

        Object value = LiteralBinder.bindLiteralAsType(
            lit,
            runtimeEnum,
            diags,
            bindings
        );

        // Bind default enum value to the LITERAL node, not the enum decl
        // (EnumDecl is already mapped to EnumType by TypeBuilder)
        bindings.put(lit, value);
    }

    // ─────────────────────────────────────────────
    // 4. Union default
    // ─────────────────────────────────────────────

    private static void bindUnionDefault(
        UnionDecl unionDecl,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        if (unionDecl.defaultValue().isEmpty()) {
            return;
        }

        UnionLiteralNode lit = unionDecl.defaultValue().get();

        // Get the Name from symbols
        kafkasql.runtime.Name unionName = symbols.nameOf(unionDecl).orElseThrow(() ->
            new IllegalStateException("UnionDecl not in symbol table")
        );

        // Build the runtime UnionType from the AST declaration
        UnionType runtimeUnion = ComplexTypeFactory.fromUnionDecl(unionDecl, unionName);

        Object value = LiteralBinder.bindLiteralAsType(
            lit,
            runtimeUnion,
            diags,
            bindings
        );

        // Bind default union value to the LITERAL node, not the union decl
        // (UnionDecl is already mapped to UnionType by TypeBuilder)
        bindings.put(lit, value);
    }
}