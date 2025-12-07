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
import kafkasql.lang.syntax.ast.decl.StreamDecl;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.stmt.UseStmt;
import kafkasql.lang.syntax.ast.use.ContextUse;

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
