package kafkasql.engine;

import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.runtime.Name;

/**
 * Listener for semantic model mutations.
 * <p>
 * Implementations are notified when the engine detects new symbol
 * registrations after a successful DDL binding pass. This is the
 * hook for persisting model changes to a backing store (e.g., Kafka topic).
 *
 * @see KafkaSqlEngine#setModelChangeListener(ModelChangeListener)
 */
@FunctionalInterface
public interface ModelChangeListener {

    /**
     * Called when a new symbol has been registered in the symbol table.
     *
     * @param name           Fully qualified name of the created object
     * @param decl           The declaration AST node
     * @param statementText  The original DDL statement text that created the symbol
     * @throws Exception if persisting the change fails
     */
    void onSymbolCreated(Name name, Decl decl, String statementText) throws Exception;
}
