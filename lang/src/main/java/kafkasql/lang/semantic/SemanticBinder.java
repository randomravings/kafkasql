package kafkasql.lang.semantic;

import java.util.List;

import kafkasql.lang.semantic.resolve.TypeResolver;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.semantic.resolve.ContextScope;
import kafkasql.lang.semantic.resolve.DeclResolver;
import kafkasql.lang.semantic.bind.StatementBinder;
import kafkasql.lang.semantic.bind.TypeBuilder;
import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.semantic.bind.DefaultBinder;

public final class SemanticBinder {

    /**
     * Bind scripts starting with a fresh SymbolTable.
     */
    public static SemanticModel bind(List<Script> scripts, Diagnostics diags) {
        SymbolTable symbols = new SymbolTable();
        return bind(scripts, symbols, diags);
    }

    /**
     * Bind scripts using an existing SymbolTable (for persistent semantic model).
     * This allows CREATE statements to accumulate across multiple execute() calls.
     */
    public static SemanticModel bind(List<Script> scripts, SymbolTable symbols, Diagnostics diags) {
        BindingEnv bindings = new BindingEnv();
        ContextScope scope = new ContextScope();

        for (Script script : scripts) {

            // PHASE A: collect symbols
            DeclResolver.collectSymbols(script, symbols, scope, diags);

            // PHASE B: resolve type refs
            TypeResolver.resolve(script, symbols, bindings, diags);

            // PHASE B+: build runtime types
            TypeBuilder.buildTypes(script, symbols, bindings, diags);

            // PHASE C1: defaults
            DefaultBinder.bindDefaults(script, symbols, bindings, diags);

            // PHASE C2: statements
            StatementBinder.bind(script, symbols, bindings, diags);
        }

        return new SemanticModel(symbols, bindings, diags);
    }
}