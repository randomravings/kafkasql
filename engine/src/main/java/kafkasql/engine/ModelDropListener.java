package kafkasql.engine;

import kafkasql.runtime.Name;

/**
 * Listener for DROP mutations.
 * <p>
 * Implementations are notified when the engine detects symbol
 * removals after a successful DDL binding pass.
 *
 * @see KafkaSqlEngine#setModelDropListener(ModelDropListener)
 */
@FunctionalInterface
public interface ModelDropListener {

    /**
     * Called when a symbol has been removed from the symbol table.
     *
     * @param name           Fully qualified name of the dropped object
     * @param statementText  The original DDL statement text
     * @throws Exception if persisting the change fails
     */
    void onSymbolDropped(Name name, String statementText) throws Exception;
}
