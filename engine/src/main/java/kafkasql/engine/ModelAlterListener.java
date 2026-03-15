package kafkasql.engine;

import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.runtime.Name;

import java.util.Map;

/**
 * Listener for ALTER mutations.
 * <p>
 * Implementations are notified when the engine detects ALTER statements
 * after a successful DDL binding pass. Receives the stream offsets
 * of schema-change markers written to affected data topics.
 *
 * @see KafkaSqlEngine#setModelAlterListener(ModelAlterListener)
 */
@FunctionalInterface
public interface ModelAlterListener {

    /**
     * Called when a symbol has been altered in the symbol table.
     *
     * @param name           Fully qualified name of the altered object
     * @param decl           The updated declaration AST node
     * @param statementText  The original DDL statement text
     * @param streamOffsets  Map of stream name → (partition → offset) for schema markers
     * @throws Exception if persisting the change fails
     */
    void onSymbolAltered(Name name, Decl decl, String statementText,
                         Map<String, Map<Integer, Long>> streamOffsets) throws Exception;
}
