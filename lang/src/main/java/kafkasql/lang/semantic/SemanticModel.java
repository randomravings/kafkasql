package kafkasql.lang.semantic;

import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.semantic.symbol.SymbolTable;

import java.util.List;
import java.util.Optional;

/**
 * SemanticModel
 *
 * Represents the result of the semantic analysis phase:
 *  - collected symbols
 *  - semantic bindings (AST node -> semantic object)
 *  - diagnostics
 *
 * The BindingEnv provides typed lookup helpers, but is still
 * assignable to Map<Object,Object> for backward compatibility.
 */
public final class SemanticModel {

    private final SymbolTable symbols;
    private final BindingEnv bindings;
    private final Diagnostics diagnostics;

    public SemanticModel(
            SymbolTable symbols,
            BindingEnv bindings,
            Diagnostics diagnostics
    ) {
        this.symbols = symbols;
        this.bindings = bindings;
        this.diagnostics = diagnostics;
    }

    // ----------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------

    public SymbolTable symbols() {
        return symbols;
    }

    public BindingEnv bindings() {
        return bindings;
    }

    public Diagnostics diags() {
        return diagnostics;
    }

    // Typed lookup helpers
    public <T> Optional<T> get(Object key, Class<T> type) {
        return bindings.get(key, type);
    }

    public <T> T getOrNull(Object key, Class<T> type) {
        return bindings.getOrNull(key, type);
    }

    // For convenience
    public boolean hasErrors() {
        return diagnostics.hasError();
    }

    public boolean hasFatal() {
        return diagnostics.hasFatal();
    }

    public List<?> allErrors() {
        return diagnostics.all();
    }
}