package kafkasql.lang.semantic.bind;

import kafkasql.runtime.*;
import kafkasql.runtime.type.*;
import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.factory.ComplexTypeFactory;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberInlineDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberRefDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.expr.Expr;
import kafkasql.lang.syntax.ast.fragment.*;
import kafkasql.lang.syntax.ast.literal.StructFieldLiteralNode;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.type.ComplexTypeNode;

import java.util.*;

public final class StatementBinder {

    private StatementBinder() {}

    // ========================================================================
    // ENTRY POINT
    // ========================================================================

    public static void bind(
        Script script,
        SymbolTable symbols,
        BindingEnv bindings,
        Diagnostics diags
    ) {
        for (Stmt stmt : script.statements()) {
            switch (stmt) {

                case ReadStmt r  -> bindRead(r, symbols, diags, bindings);
                case WriteStmt w -> bindWrite(w, symbols, diags, bindings);
                default -> {}
            }
        }
    }

    // ========================================================================
    // READ BINDING
    // ========================================================================

    private static void bindRead(
        ReadStmt stmt,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        Name streamFqn = toName(stmt.stream());
        Optional<StreamDecl> opt = symbols.lookupStream(streamFqn);

        if (opt.isEmpty()) {
            diags.error(
                stmt.stream().range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_STREAM,
                "Unknown stream: " + streamFqn
            );
            return;
        }

        StreamDecl streamDecl = opt.get();

        for (ReadTypeBlock block : stmt.blocks()) {
            bindReadBlock(block, streamDecl, symbols, diags, bindings);
        }
    }

    private static void bindReadBlock(
        ReadTypeBlock block,
        StreamDecl streamDecl,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        Identifier alias = block.alias();

        // 1) Locate stream member
        StreamMemberDecl member = findStreamMember(streamDecl, alias, diags);
        if (member == null) return;

        // 2) Build row type
        StructType rowType = buildRowStructType(streamDecl, member, symbols, diags, bindings);
        if (rowType == null) return;

        // Bind alias → rowType and unpack fields into scope
        TypeEnv env = new TypeEnv();
        env.define(alias.name(), rowType);
        
        // Unpack struct fields so they can be referenced directly (e.g., "Name" instead of "alias.Name")
        for (var field : rowType.fields().values()) {
            env.define(field.name(), field.type());
        }
        
        bindings.put(block, rowType);

        ExpressionBinder exprBinder =
            new ExpressionBinder(env, symbols, diags, bindings);

        // -----------------------------
        // PROJECTION
        // -----------------------------
        ProjectionNode projection = block.projection();

        if (projection.items().isEmpty()) {
            // SELECT *
            bindings.put(projection, rowType);
        } else {
            for (ProjectionExprNode pe : projection.items()) {
                Expr expr = pe.expr();
                AnyType result = exprBinder.bind(expr);

                if (result == null) {
                    diags.error(
                        expr.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.INVALID_PROJECTION,
                        "Invalid projection expression"
                    );
                }

                bindings.put(expr, result);
                bindings.put(pe, result);
            }
        }

        // -----------------------------
        // WHERE clause
        // -----------------------------
        block.where().ifPresent(where -> {
            Expr expr = where.expr();
            AnyType t = exprBinder.bind(expr);

            if (!(t instanceof PrimitiveType pt) || pt.kind() != PrimitiveKind.BOOL) {
                diags.error(
                    expr.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_WHERE_TYPE,
                    "WHERE clause must be BOOLEAN, got: " + debugType(t)
                );
            }
            bindings.put(where, t);
        });
    }

    private static StreamMemberDecl findStreamMember(
        StreamDecl decl,
        Identifier alias,
        Diagnostics diags
    ) {
        for (StreamMemberDecl m : decl.streamTypes()) {
            if (m.name().name().equals(alias.name()))
                return m;
        }

        diags.error(
            alias.range(),
            DiagnosticKind.SEMANTIC,
            DiagnosticCode.UNKNOWN_MEMBER,
            "Stream '" + decl.name().name() +
                "' has no member named '" + alias.name() + "'"
        );
        return null;
    }

    private static StructType buildRowStructType(
        StreamDecl streamDecl,
        StreamMemberDecl member,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        // INLINE STRUCT
        if (member instanceof StreamMemberInlineDecl inline) {
            // Create a synthetic Name for inline struct: stream.member
            String streamFqn = streamDecl.name().name();
            String memberName = inline.name().name();
            Name inlineFqn = Name.of(streamFqn, memberName);
            LinkedHashMap<String, StructTypeField> fields = new LinkedHashMap<>();

            for (StructFieldDecl f : inline.fields()) {
                String fieldName = f.name().name();
                
                // Use TypeBuilder to resolve complex type references
                AnyType type = TypeBuilder.resolveFieldType(
                    f.type(),
                    symbols,
                    bindings,
                    diags
                );
                
                boolean nullable = f.nullable();

                Optional<Object> defaultValue = f.defaultValue()
                    .map(lit -> Optional.of(
                        LiteralBinder.bindLiteralAsType(lit, type, diags, bindings)
                    ))
                    .orElse(Optional.empty());

                Optional<String> doc = extractDoc(f.doc());

                fields.put(fieldName,
                    new StructTypeField(
                        fieldName,
                        type,
                        nullable,
                        defaultValue,
                        doc
                    )
                );
            }

            StructType rowType =
                new StructType(inlineFqn, fields, Optional.empty());

            bindings.put(inline, rowType);
            return rowType;
        }

        // REF STRUCT
        if (member instanceof StreamMemberRefDecl refDecl) {
            Object resolved = bindings.get(refDecl.ref());

            if (!(resolved instanceof StructDecl structDecl)) {
                diags.error(
                    refDecl.ref().range(),
                    DiagnosticKind.TYPE,
                    DiagnosticCode.INVALID_TYPE_REF,
                    "TypeRefNode not bound to StructDecl"
                );
                return null;
            }

            // Get the pre-built runtime type from TypeBuilder
            StructType rowType = bindings.getOrNull(structDecl, StructType.class);
            if (rowType == null) {
                // Fallback if TypeBuilder didn't run (shouldn't happen)
                // Get the Name from symbols
                Name structName = symbols.nameOf(structDecl).orElseThrow(() ->
                    new IllegalStateException("StructDecl not in symbol table")
                );
                rowType = ComplexTypeFactory.fromStructDecl(structDecl, structName, diags);
            }
            
            bindings.put(refDecl, rowType);
            return rowType;
        }

        diags.error(
            member.range(),
            DiagnosticKind.SEMANTIC,
            DiagnosticCode.INTERNAL_ERROR,
            "Unsupported stream member type: " +
                member.getClass().getSimpleName()
        );
        return null;
    }

    // ========================================================================
    // WRITE BINDING
    // ========================================================================

    private static void bindWrite(
        WriteStmt stmt,
        SymbolTable symbols,
        Diagnostics diags,
        BindingEnv bindings
    ) {
        Name streamFqn = toName(stmt.stream());
        Optional<StreamDecl> opt = symbols.lookupStream(streamFqn);

        if (opt.isEmpty()) {
            diags.error(
                stmt.stream().range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_STREAM,
                "Unknown stream: " + streamFqn
            );
            return;
        }

        StreamDecl streamDecl = opt.get();
        StreamMemberDecl member =
            findStreamMember(streamDecl, stmt.alias(), diags);

        if (member == null) return;

        StructType rowType =
            buildRowStructType(streamDecl, member, symbols, diags, bindings);

        if (rowType == null) return;

        // Bind each struct literal against the known rowType
        for (StructLiteralNode lit : stmt.values()) {
            Object v = LiteralBinder.bindLiteralAsType(
                lit, rowType, diags, bindings
            );
            bindings.put(lit, v);
            
            // Validate that all required fields are present
            validateStructLiteralCompleteness(
                lit, rowType, member, streamDecl, symbols, diags
            );
        }

        bindings.put(stmt, rowType);
    }
    
    /**
     * Validates that a struct literal provides all required fields.
     * A field can be omitted only if it is nullable OR has a default value.
     */
    private static void validateStructLiteralCompleteness(
        StructLiteralNode lit,
        StructType rowType,
        StreamMemberDecl member,
        StreamDecl streamDecl,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        // Collect field names provided in the literal
        Set<String> providedFields = new HashSet<>();
        for (StructFieldLiteralNode f : lit.fields()) {
            providedFields.add(f.name().name());
        }
        
        // Check each field in the struct type
        for (var entry : rowType.fields().entrySet()) {
            String fieldName = entry.getKey();
            StructTypeField field = entry.getValue();
            
            if (providedFields.contains(fieldName)) {
                continue; // Field is provided
            }
            
            // Field is missing - check if it's allowed
            boolean hasDefault = hasDefault(member, fieldName, streamDecl, symbols);
            boolean isNullable = field.nullable();
            
            if (!hasDefault && !isNullable) {
                diags.error(
                    lit.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.MISSING_FIELD,
                    "Required field '" + fieldName + "' is missing from struct literal. " +
                    "Field must be provided, or the field must be nullable or have a default value."
                );
            }
        }
    }
    
    /**
     * Checks if a field has a default value by looking at the AST declaration.
     */
    private static boolean hasDefault(
        StreamMemberDecl member,
        String fieldName,
        StreamDecl streamDecl,
        SymbolTable symbols
    ) {
        // For inline structs, check the inline field declarations
        if (member instanceof StreamMemberInlineDecl inline) {
            for (StructFieldDecl f : inline.fields()) {
                if (f.name().name().equals(fieldName)) {
                    return f.defaultValue().isPresent();
                }
            }
            return false;
        }
        
        // For ref structs, need to find the StructDecl
        if (member instanceof StreamMemberRefDecl refDecl) {
            ComplexTypeNode typeRef = refDecl.ref();
            QName qname = typeRef.name();
            Name typeName = Name.of(qname.context(), qname.name());
            Optional<StructDecl> opt = symbols.lookupStruct(typeName);
            
            if (opt.isPresent()) {
                StructDecl structDecl = opt.get();
                for (StructFieldDecl f : structDecl.fields()) {
                    if (f.name().name().equals(fieldName)) {
                        return f.defaultValue().isPresent();
                    }
                }
            }
        }
        
        return false;
    }

    // ========================================================================
    // UTIL
    // ========================================================================

    private static String debugType(AnyType t) {
        if (t == null) return "<null>";
        if (t instanceof ComplexType ct) return ct.fqn().toString();
        return t.getClass().getSimpleName();
    }

    private static Optional<String> extractDoc(TypedOptional<DocNode> docNode) {
        if (docNode.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(docNode.get().comment());
        }
    }

    private static Name toName(QName q) {
        return Name.of(q.context(), q.name());
    }
}