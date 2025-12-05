package kafkasql.lang.semantic.bind;

import kafkasql.runtime.*;
import kafkasql.runtime.type.*;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.semantic.util.FragmentUtils;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.expr.Expr;
import kafkasql.lang.syntax.ast.fragment.*;
import kafkasql.lang.syntax.ast.literal.StructFieldLiteralNode;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.lang.syntax.ast.misc.Identifier;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.stmt.*;

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
        if (block.where().isPresent()) {
            Expr expr = block.where().get().expr();
            AnyType t = exprBinder.bind(expr);

            if (!(t instanceof PrimitiveType pt) || pt.kind() != PrimitiveKind.BOOL) {
                diags.error(
                    expr.range(),
                    DiagnosticKind.SEMANTIC,
                    DiagnosticCode.INVALID_WHERE_TYPE,
                    "WHERE clause must be BOOLEAN, got: " + debugType(t)
                );
            }
            bindings.put(block.where().get(), t);
        }
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
        TypeDecl typeDecl = member.memberDecl();
        
        // Check if it's an inline STRUCT definition
        if (typeDecl.kind() instanceof StructDecl structDecl) {
            // Create a synthetic Name for inline struct: stream.member
            String streamFqn = streamDecl.name().name();
            String memberName = member.name().name();
            Name inlineFqn = Name.of(streamFqn, memberName);
            LinkedHashMap<String, StructTypeField> fields = new LinkedHashMap<>();

            for (StructFieldDecl f : structDecl.fields()) {
                String fieldName = f.name().name();
                
                // Use TypeBuilder to resolve complex type references
                AnyType type = TypeBuilder.resolveFieldType(
                    f.type(),
                    symbols,
                    bindings,
                    diags
                );
                
                boolean nullable = f.nullable().isPresent();

                Optional<Object> defaultValue = FragmentUtils.extractDefault(f.fragments(), diags);

                Optional<String> doc = FragmentUtils.extractDoc(f.fragments(), diags);

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
                new StructType(inlineFqn, fields, List.of(), Optional.empty());

            bindings.put(structDecl, rowType);
            return rowType;
        }

        // Handle DerivedTypeDecl - resolve to the actual referenced type
        if (typeDecl.kind() instanceof DerivedTypeDecl derivedType) {
            // Get the referenced TypeDecl from bindings (set by TypeResolver)
            Object resolved = bindings.get(derivedType.target());
            if (resolved instanceof TypeDecl referencedTypeDecl) {
                // Get the runtime type for the referenced declaration
                StructType baseRowType = bindings.getOrNull(referencedTypeDecl, StructType.class);
                if (baseRowType != null && referencedTypeDecl.kind() instanceof StructDecl refStructDecl) {
                    // Need to rebuild StructType with defaults from DefaultBinder
                    // The defaults are stored in bindings keyed by StructFieldDecl
                    LinkedHashMap<String, StructTypeField> fieldsWithDefaults = new LinkedHashMap<>();
                    
                    for (StructFieldDecl fieldDecl : refStructDecl.fields()) {
                        String fieldName = fieldDecl.name().name();
                        StructTypeField baseField = baseRowType.fields().get(fieldName);
                        
                        if (baseField != null) {
                            // Check if DefaultBinder stored a default for this field
                            Object defaultValue = bindings.getOrNull(fieldDecl, Object.class);
                            
                            StructTypeField fieldWithDefault = new StructTypeField(
                                baseField.name(),
                                baseField.type(),
                                baseField.nullable(),
                                defaultValue != null ? Optional.of(defaultValue) : baseField.defaultValue(),
                                baseField.doc()
                            );
                            
                            fieldsWithDefaults.put(fieldName, fieldWithDefault);
                        }
                    }
                    
                    StructType rowType = new StructType(
                        baseRowType.fqn(),
                        fieldsWithDefaults,
                        baseRowType.constraints(),
                        baseRowType.doc()
                    );
                    
                    bindings.put(typeDecl, rowType);
                    return rowType;
                }
            }
            
            diags.error(
                member.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INTERNAL_ERROR,
                "Could not resolve derived type reference: " + derivedType.target()
            );
            return null;
        }

        diags.error(
            member.range(),
            DiagnosticKind.SEMANTIC,
            DiagnosticCode.INTERNAL_ERROR,
            "Stream member type must be a STRUCT or reference to STRUCT, got: " +
                typeDecl.kind().getClass().getSimpleName()
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
        TypeDecl typeDecl = member.memberDecl();
        
        // For inline structs, check the inline field declarations
        if (typeDecl.kind() instanceof StructDecl structDecl) {
            for (StructFieldDecl f : structDecl.fields()) {
                if (f.name().name().equals(fieldName)) {
                    return f.fragments().stream().anyMatch(frag -> frag instanceof DefaultNode);
                }
            }
            return false;
        }
        
        // For derived types, resolve to the actual referenced type
        if (typeDecl.kind() instanceof DerivedTypeDecl derivedType) {
            // Look up the referenced type
            Optional<TypeDecl> refTypeOpt = symbols.lookupType(toName(derivedType.target().name()));
            if (refTypeOpt.isPresent() && refTypeOpt.get().kind() instanceof StructDecl structDecl) {
                for (StructFieldDecl f : structDecl.fields()) {
                    if (f.name().name().equals(fieldName)) {
                        return f.fragments().stream().anyMatch(frag -> frag instanceof DefaultNode);
                    }
                }
            }
            return false;
        }
        
        // Fallback: try to look up in symbols using stream.member name
        // (This path might not be needed anymore with proper DerivedTypeDecl handling)
        String streamFqn = streamDecl.name().name();
        String memberName = member.name().name();
        Name lookupName = Name.of(streamFqn, memberName);
        
        Optional<StructDecl> opt = symbols.lookupStruct(lookupName);
        if (opt.isPresent()) {
            StructDecl structDecl = opt.get();
            for (StructFieldDecl f : structDecl.fields()) {
                if (f.name().name().equals(fieldName)) {
                    return f.fragments().stream().anyMatch(frag -> frag instanceof DefaultNode);
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

    private static Name toName(QName q) {
        return Name.of(q.context(), q.name());
    }
}