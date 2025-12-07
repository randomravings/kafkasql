package kafkasql.lang.semantic.bind;

import java.util.Map;

import kafkasql.runtime.Name;
import kafkasql.runtime.type.*;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.factory.ComplexTypeFactory;
import kafkasql.lang.semantic.factory.PrimitiveTypeFactory;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.EnumDecl;
import kafkasql.lang.syntax.ast.decl.ScalarDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.decl.UnionDecl;
import kafkasql.lang.syntax.ast.fragment.DefaultNode;
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

            if (!(ct.decl() instanceof TypeDecl typeDecl))
                continue;

            Name name = symbols.nameOf(typeDecl).orElseThrow(() ->
                new IllegalStateException("TypeDecl not in symbol table")
            );

            switch (typeDecl.kind()) {
                case ScalarDecl s -> bindScalarDefault(typeDecl, diags, bindings);
                case StructDecl s -> bindStructDefaults(typeDecl, symbols, diags, bindings);
                case EnumDecl   e -> bindEnumDefault(name, typeDecl, symbols, diags, bindings);
                case UnionDecl  u -> bindUnionDefault(name, typeDecl, symbols, diags, bindings);
                case DerivedTypeDecl d -> { } // Derived types don't have their own defaults
            }
        }
    }

    // ─────────────────────────────────────────────
    // 1. Scalar default
    // ─────────────────────────────────────────────

    private static void bindScalarDefault(
        TypeDecl typeDecl,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        ScalarDecl scalar = (ScalarDecl) typeDecl.kind();
        
        // Find DEFAULT fragment
        DefaultNode defaultNode = typeDecl.fragments().stream()
            .filter(f -> f instanceof DefaultNode)
            .map(f -> (DefaultNode) f)
            .findFirst()
            .orElse(null);
            
        if (defaultNode == null)
            return;

        LiteralNode lit = defaultNode.value();

        // Expected runtime type is the primitive base type
        // scalar.type() is TypeNode, cast to PrimitiveTypeNode
        if (!(scalar.type() instanceof kafkasql.lang.syntax.ast.type.PrimitiveTypeNode ptn))
            return;
            
        PrimitiveType baseType = PrimitiveTypeFactory.fromAst(ptn);

        Object value = LiteralBinder.bindLiteralAsType(
            lit,
            baseType,
            diags,
            bindings
        );

        // Bind default value to the type declaration
        bindings.put(typeDecl, value);
    }

    // ─────────────────────────────────────────────
    // 2. Struct field defaults
    // ─────────────────────────────────────────────

    private static void bindStructDefaults(
        TypeDecl typeDecl,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        StructDecl struct = (StructDecl) typeDecl.kind();
        
        // Get the pre-built runtime type from TypeBuilder
        StructType structType = bindings.getOrNull(typeDecl, StructType.class);
        if (structType == null) {
            // Shouldn't happen if TypeBuilder ran
            return;
        }

        for (StructFieldDecl field : struct.fields()) {

            // Find DEFAULT fragment
            DefaultNode defaultNode = field.fragments().stream()
                .filter(f -> f instanceof DefaultNode)
                .map(f -> (DefaultNode) f)
                .findFirst()
                .orElse(null);
                
            if (defaultNode == null)
                continue;

            LiteralNode lit = defaultNode.value();

            // Get field type from the runtime struct type (case-insensitive)
            String fieldName = field.name().name();
            StructTypeField fieldInfo = structType.fields().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(fieldName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
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
        Name name,
        TypeDecl typeDecl,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        EnumDecl enumDecl = (EnumDecl) typeDecl.kind();
        
        // Find DEFAULT fragment
        DefaultNode defaultNode = typeDecl.fragments().stream()
            .filter(f -> f instanceof DefaultNode)
            .map(f -> (DefaultNode) f)
            .findFirst()
            .orElse(null);
            
        if (defaultNode == null)
            return;

        LiteralNode lit = defaultNode.value();

        // Build the runtime EnumType from the AST declaration
        EnumType runtimeEnum = ComplexTypeFactory.fromEnumDecl(name, enumDecl, typeDecl.fragments(), diags);

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
        Name name,
        TypeDecl typeDecl,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        UnionDecl unionDecl = (UnionDecl) typeDecl.kind();
        
        // Find DEFAULT fragment
        DefaultNode defaultNode = typeDecl.fragments().stream()
            .filter(f -> f instanceof DefaultNode)
            .map(f -> (DefaultNode) f)
            .findFirst()
            .orElse(null);
            
        if (defaultNode == null)
            return;

        // Default for union must be a UnionLiteralNode
        if (!(defaultNode.value() instanceof UnionLiteralNode lit))
            return;

        // Build the runtime UnionType from the AST declaration
        UnionType runtimeUnion = ComplexTypeFactory.fromUnionDecl(name, unionDecl, typeDecl.fragments(), diags);

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