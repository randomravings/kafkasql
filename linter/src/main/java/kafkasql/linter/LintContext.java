package kafkasql.linter;

import java.util.Optional;

import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.runtime.Name;

/**
 * Context provided to lint rules during analysis.
 * 
 * <p>Provides access to:
 * <ul>
 *   <li>Issue reporting with configurable severity
 *   <li>Symbol table for type lookups
 *   <li>Binding environment for semantic information
 * </ul>
 * 
 * @apiNote This is a stable API. New methods may be added but existing
 * methods will not be removed or changed in minor versions.
 */
public interface LintContext {
    
    /**
     * Reports a lint issue with the rule's default severity.
     * 
     * @param range source location of the issue
     * @param message human-readable message
     */
    void report(Range range, String message);
    
    /**
     * Reports a lint issue with specified severity.
     * 
     * @param range source location of the issue
     * @param severity severity level
     * @param message human-readable message
     */
    void report(Range range, DiagnosticEntry.Severity severity, String message);
    
    /**
     * Returns the symbol table for type lookups.
     */
    SymbolTable symbols();
    
    /**
     * Returns the binding environment with semantic information.
     */
    BindingEnv bindings();
    
    /**
     * Looks up a type by name.
     * 
     * @param name fully qualified type name
     * @return type declaration if found
     */
    default Optional<TypeDecl> lookupType(Name name) {
        return symbols().lookupType(name);
    }
}
