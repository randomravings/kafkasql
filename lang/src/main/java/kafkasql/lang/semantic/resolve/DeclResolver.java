package kafkasql.lang.semantic.resolve;

import kafkasql.runtime.*;
import kafkasql.lang.diagnostics.DiagnosticCode;
import kafkasql.lang.diagnostics.DiagnosticKind;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.ContextDecl;
import kafkasql.lang.syntax.ast.decl.Decl;
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
        if ((stmt.decl() instanceof Decl c))
            canonicalFqn = anyName(scope, diags, c);
        else
            canonicalFqn = contextName(scope, diags, stmt.decl());

        if (duplicate(canonicalFqn.get(), symbols, diags, stmt.range()))
            return;
        
        symbols.register(canonicalFqn.get(), stmt.decl());
    }

    // =======================================================================
    // Helpers
    // =======================================================================
    
    /**
     * Construct canonical context name from simple identifier
     * Returns empty if context is global (error reported)
     * 
     * @param scope
     * @param diags
     * @param decl
     * @return
     */
    private static Optional<Name> contextName(ContextScope scope, Diagnostics diags, Decl decl) {
        if (scope.isGlobal()) {
            diags.error(
                decl.range(),
                DiagnosticKind.SEMANTIC,
                DiagnosticCode.INVALID_TYPE_REF,
                "Types must be created inside a context. " +
                "Call USE CONTEXT <name>; before CREATE TYPE."
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
