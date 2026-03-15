package kafkasql.lang.semantic.resolve;

import kafkasql.runtime.*;
import kafkasql.runtime.diagnostics.DiagnosticCode;
import kafkasql.runtime.diagnostics.DiagnosticKind;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.decl.DerivedTypeDecl;
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.StreamMemberDecl;
import kafkasql.lang.syntax.ast.decl.StructDecl;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.fragment.DroppedNode;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.stmt.AlterStmt;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.DropStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.stmt.UseStmt;
import kafkasql.lang.syntax.ast.use.ContextUse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeclResolver {

    private DeclResolver() {}

    public static void collectSymbols(
        Script script,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {

        for (Stmt stmt : script.statements()) {
            switch (stmt) {
                case UseStmt s -> resolveUseStmt(s, symbols, scope, diags);
                case CreateStmt c -> resolveCreateStmt(c, symbols, scope, diags);
                case AlterStmt a -> resolveAlterStmt(a, symbols, diags);
                case DropStmt d -> resolveDropStmt(d, symbols, diags);
                default -> {
                    // ignore other statements
                }
            }
        }
    }

    private static void resolveUseStmt(
        UseStmt stmt,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {
        switch (stmt.target()) {
            case ContextUse uc -> {
                // Check for GLOBAL context (empty QName)
                if (uc.qname().isRoot()) {
                    // Return to global/root context
                    scope.set(Name.ROOT);
                    return;
                }
                
                Name fqn = toName(uc.qname());
                Optional<ContextDecl> ctxDecl = symbols.lookupContext(fqn);

                if (ctxDecl.isEmpty()) {
                    diags.error(
                        uc.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_CONTEXT,
                        "Unknown context: " + fqn
                    );
                    // keep current scope unchanged on error
                } else {
                    scope.set(fqn);
                }
            }
        }
    }

    private static void resolveCreateStmt(
        CreateStmt stmt,
        SymbolTable symbols,
        ContextScope scope,
        Diagnostics diags
    ) {
        Optional<Name> canonicalFqn = Optional.empty();
        
        // Only STREAMs require an active context
        // ContextDecl and TypeDecl can be created at global scope
        if (stmt.decl() instanceof StreamDecl) {
            canonicalFqn = requireActiveContext(scope, diags, stmt.decl());
            if (canonicalFqn.isEmpty())
                return;
        } else {
            canonicalFqn = anyName(scope, diags, stmt.decl());
        }

        if (duplicate(canonicalFqn.get(), symbols, diags, stmt.range()))
            return;
        
        symbols.register(canonicalFqn.get(), stmt.decl());
    }

    private static void resolveAlterStmt(
        AlterStmt stmt,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        Name target = toName(stmt.target());
        if (!symbols.hasKey(target)) {
            diags.error(
                stmt.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_TYPE,
                "Cannot ALTER unknown object: " + target
            );
            return;
        }

        switch (stmt) {
            case AlterStmt.AlterType at -> {
                var typeOpt = symbols.lookupType(target);
                if (typeOpt.isEmpty()) {
                    diags.error(
                        stmt.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_TYPE,
                        target + " is not a TYPE"
                    );
                    return;
                }
                applyAlterType(at, typeOpt.get(), target, symbols, diags);
            }
            case AlterStmt.AlterStream as -> {
                if (symbols.lookupStream(target).isEmpty()) {
                    diags.error(
                        stmt.range(),
                        DiagnosticKind.SEMANTIC,
                        DiagnosticCode.UNKNOWN_STREAM,
                        target + " is not a STREAM"
                    );
                }
            }
        }
    }

    private static void applyAlterType(
        AlterStmt.AlterType at,
        TypeDecl existingType,
        Name target,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        switch (at.action()) {
            case AlterStmt.AddField af -> {
                if (!(existingType.kind() instanceof StructDecl struct)) {
                    diags.error(af.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                        "Can only ADD FIELD to STRUCT types");
                    return;
                }
                String newFieldName = af.field().name().name();
                for (StructFieldDecl f : struct.fields()) {
                    if (f.name().name().equals(newFieldName)) {
                        diags.error(af.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DUPLICATE_DECLARATION,
                            "Field '" + newFieldName + "' already exists");
                        return;
                    }
                }
                AstListNode<StructFieldDecl> newFields = new AstListNode<>(StructFieldDecl.class);
                newFields.addAll(struct.fields());
                newFields.add(af.field());
                StructDecl newStruct = new StructDecl(struct.range(), newFields);
                TypeDecl newTypeDecl = new TypeDecl(
                    existingType.range(), existingType.name(), newStruct, existingType.fragments());
                symbols.replace(target, newTypeDecl);
            }
            case AlterStmt.DropMember dm -> {
                if (!(existingType.kind() instanceof StructDecl struct)) {
                    diags.error(dm.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                        "Can only DROP member from STRUCT types");
                    return;
                }
                String dropFieldName = dm.name().name();
                AstListNode<StructFieldDecl> newFields = new AstListNode<>(StructFieldDecl.class);
                boolean found = false;
                for (StructFieldDecl f : struct.fields()) {
                    if (f.name().name().equals(dropFieldName)) {
                        if (f.fragments().stream().anyMatch(frag -> frag instanceof DroppedNode)) {
                            diags.error(dm.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.INVALID_TYPE_REF,
                                "Field '" + dropFieldName + "' is already dropped");
                            return;
                        }
                        AstListNode<DeclFragment> newFragments = new AstListNode<>(DeclFragment.class);
                        newFragments.addAll(f.fragments());
                        newFragments.add(new DroppedNode(f.range()));
                        newFields.add(new StructFieldDecl(
                            f.range(), f.name(), f.type(), f.nullable(), newFragments));
                        found = true;
                    } else {
                        newFields.add(f);
                    }
                }
                if (!found) {
                    diags.error(dm.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_MEMBER,
                        "Unknown field: " + dropFieldName);
                    return;
                }
                StructDecl newStruct = new StructDecl(struct.range(), newFields);
                TypeDecl newTypeDecl = new TypeDecl(
                    existingType.range(), existingType.name(), newStruct, existingType.fragments());
                symbols.replace(target, newTypeDecl);
            }
            case AlterStmt.AddSymbol as -> {
                // TODO: handle enum symbol addition
            }
        }
    }

    private static void resolveDropStmt(
        DropStmt stmt,
        SymbolTable symbols,
        Diagnostics diags
    ) {
        Name target = toName(stmt.target());
        if (!symbols.hasKey(target)) {
            diags.error(
                stmt.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.UNKNOWN_TYPE,
                "Cannot DROP unknown object: " + target
            );
            return;
        }

        boolean hasError = false;

        switch (stmt) {
            case DropStmt.DropContext dc -> {
                if (symbols.lookupContext(target).isEmpty()) {
                    diags.error(dc.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_CONTEXT,
                        target + " is not a CONTEXT");
                    hasError = true;
                } else {
                    List<Name> children = findChildren(symbols, target);
                    if (!children.isEmpty()) {
                        diags.error(dc.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DEPENDENCY_EXISTS,
                            "Cannot DROP CONTEXT " + target + ": contains " + children.size() + " object(s)");
                        hasError = true;
                    }
                }
            }
            case DropStmt.DropType dt -> {
                if (symbols.lookupType(target).isEmpty()) {
                    diags.error(dt.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_TYPE,
                        target + " is not a TYPE");
                    hasError = true;
                } else {
                    List<Name> dependents = findTypeDependents(symbols, target);
                    if (!dependents.isEmpty()) {
                        diags.error(dt.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.DEPENDENCY_EXISTS,
                            "Cannot DROP TYPE " + target + ": referenced by " + dependents.getFirst());
                        hasError = true;
                    }
                }
            }
            case DropStmt.DropStream ds -> {
                if (symbols.lookupStream(target).isEmpty()) {
                    diags.error(ds.range(), DiagnosticKind.SEMANTIC, DiagnosticCode.UNKNOWN_STREAM,
                        target + " is not a STREAM");
                    hasError = true;
                }
            }
        }

        if (!hasError) {
            symbols._decl.remove(target);
        }
    }

    // =======================================================================
    // Dependency helpers
    // =======================================================================

    /**
     * Find all symbols that are children of the given context.
     */
    private static List<Name> findChildren(SymbolTable symbols, Name context) {
        String prefix = context.fullName() + ".";
        return symbols._decl.keySet().stream()
            .filter(n -> n.fullName().toLowerCase().startsWith(prefix.toLowerCase()))
            .toList();
    }

    /**
     * Find all symbols that reference the given type.
     * Checks stream member derived types and struct field type references.
     */
    private static List<Name> findTypeDependents(SymbolTable symbols, Name typeName) {
        return symbols._decl.entrySet().stream()
            .filter(e -> referencesType(e.getValue(), typeName))
            .map(Map.Entry::getKey)
            .toList();
    }

    private static boolean referencesType(Decl decl, Name typeName) {
        if (decl instanceof StreamDecl sd) {
            for (StreamMemberDecl member : sd.streamTypes()) {
                if (member.memberDecl().kind() instanceof DerivedTypeDecl dt) {
                    if (toName(dt.target().name()).equals(typeName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // =======================================================================
    // Helpers
    // =======================================================================
    
    /**
     * Construct canonical name for declarations that require an active context.
     * Returns empty if context is global (error reported).
     * Used for TYPE and STREAM declarations.
     * 
     * @param scope current context scope
     * @param diags diagnostics
     * @param decl declaration (TypeDecl or StreamDecl)
     * @return fully-qualified name
     */
    private static Optional<Name> requireActiveContext(ContextScope scope, Diagnostics diags, Decl decl) {
        if (scope.isGlobal()) {
            String declType = switch (decl) {
                case TypeDecl t -> "TYPE";
                case StreamDecl s -> "STREAM";
                default -> "declaration";
            };
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                declType + " must be created inside a context. " +
                "Use CREATE CONTEXT <name>; and USE CONTEXT <name>; first."
            );
            return Optional.empty();
        }
        return anyName(scope, diags, decl);
    }

    /**
     * Construct canonical context name from simple identifier
     * Returns empty if name is empty (error reported)
     *
     * @param scope current context scope
     * @param diags diagnostics
     * @param decl declaration
     * @return fully-qualified context name
     */
    private static Optional<Name> anyName(ContextScope scope, Diagnostics diags, Decl decl) {
        if (decl.name().name().isEmpty()) {
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                "Name cannot be empty."
            );
            return Optional.empty();
        }

        Name name = scope.qualify(decl.name().name());

        if (name.isRoot()) {
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_CONTEXT_SCOPE,
                "Cannot create context with empty name."
            );
            return Optional.empty();
        }

        return Optional.of(name);
    }

    private static boolean duplicate(
        Name name,
        SymbolTable symbols,
        Diagnostics diags,
        Range range
    ) {
        if (symbols.hasKey(name)) {
            diags.error(
                range,
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.DUPLICATE_DECLARATION,
                "Unknown declaration: " + name
            );
            return true;
        }
        return false;
    }

    private static Name toName(QName q) {
        return Name.of(q.context(), q.name());
    }
}
