package kafkasql.persistence;

import kafkasql.lang.GrammarVersion;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamReader;
import sys.schema.SymbolEventLog;

/**
 * Reads events from SymbolEventLog stream and applies them to a SymbolTable.
 * 
 * This enables event sourcing - rebuilding the symbol table state from
 * the event log stored in Kafka.
 * 
 * Usage:
 * <pre>
 * var reader = SymbolEventLog.reader(consumer);
 * var symbolTable = new SymbolTable();
 * var logReader = new EventLogReader(reader, symbolTable);
 * 
 * logReader.replayAll();  // Rebuild from all events
 * // or
 * logReader.readNext();   // Process one event at a time
 * </pre>
 */
public class EventLogReader {
    
    private final StreamReader<SymbolEventLog> reader;
    private final SymbolTable symbolTable;
    
    public EventLogReader(StreamReader<SymbolEventLog> reader, SymbolTable symbolTable) {
        this.reader = reader;
        this.symbolTable = symbolTable;
    }
    
    /**
     * Reads and applies the next event from the stream.
     * 
     * @return true if an event was processed, false if no events available
     * @throws Exception if reading or applying the event fails
     */
    public boolean readNext() throws Exception {
        SymbolEventLog event = reader.read();
        if (event == null) {
            return false;
        }
        
        if (event instanceof SymbolEventLog.SymbolEvent symbolEvent) {
            applyEvent(symbolEvent);
            return true;
        }
        
        return false;
    }
    
    /**
     * Replays all available events from the stream to rebuild symbol table state.
     * Blocks until no more events are available.
     * 
     * @return Number of events processed
     * @throws Exception if reading or applying events fails
     */
    public int replayAll() throws Exception {
        int count = 0;
        while (readNext()) {
            count++;
        }
        return count;
    }
    
    /**
     * Applies a single event to the symbol table.
     */
    private void applyEvent(SymbolEventLog.SymbolEvent event) throws Exception {
        int grammarVersion = event.GrammarVersion();
        if (grammarVersion > GrammarVersion.CURRENT) {
            throw new UnsupportedOperationException(
                "Event requires grammar version " + grammarVersion +
                " but this reader only supports up to version " + GrammarVersion.CURRENT +
                ". Upgrade kafkasql to process this event."
            );
        }
        
        Name objectName = Name.of(event.ObjectName());
        
        switch (event.EventType()) {
            case CREATE_STMT, ALTER_STMT -> {
                if (event.State() != null) {
                    Decl decl = parseState(grammarVersion, event.State());
                    symbolTable.register(objectName, decl);
                }
            }
            case DROP_STMT -> {
                symbolTable._decl.remove(objectName);
            }
        }
    }
    
    /**
     * Parses a State string back into a Decl using the grammar version
     * that produced it.
     * 
     * Since State stores the full DDL statement text, we can re-parse it
     * to reconstruct the AST. The grammar version tells us which parser
     * rules apply.
     * 
     * @param grammarVersion The grammar version used when the event was written
     * @param state The DDL statement text
     * @return The parsed declaration
     */
    private Decl parseState(int grammarVersion, String state) {
        // Version 1 is the initial grammar - parse using current parser
        // When grammar evolves, add version-specific handling here:
        //   case 2 -> parseV2(state)
        //   case 3 -> parseV3(state)
        // Older versions may need migration transforms before parsing
        throw new UnsupportedOperationException(
            "State parsing not yet implemented for grammar version " + grammarVersion +
            ". Need to implement DDL-to-Decl parser integration."
        );
    }
}
