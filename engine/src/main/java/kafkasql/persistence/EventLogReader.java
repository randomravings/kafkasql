package kafkasql.persistence;

import kafkasql.lang.GrammarVersion;
import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamReader;
import sys.schema.SymbolEventLog;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    private final StreamOffsetIndex offsetIndex;
    
    public EventLogReader(StreamReader<SymbolEventLog> reader, SymbolTable symbolTable) {
        this(reader, symbolTable, null);
    }
    
    public EventLogReader(StreamReader<SymbolEventLog> reader, SymbolTable symbolTable,
                          StreamOffsetIndex offsetIndex) {
        this.reader = reader;
        this.symbolTable = symbolTable;
        this.offsetIndex = offsetIndex;
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
                // Extract stream offsets from ALTER Delta field
                if (event.EventType() == sys.schema.EventType.ALTER_STMT
                        && offsetIndex != null && event.Delta() != null) {
                    Map<String, Map<Integer, Long>> offsets =
                        EventLogWriter.extractOffsets(event.Delta());
                    if (!offsets.isEmpty()) {
                        offsetIndex.record(offsets, event.ObjectVersion());
                    }
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
        // Parse the DDL statement text back into an AST
        // When grammar evolves, add version-specific handling here:
        //   case 2 -> parseV2(state)
        //   case 3 -> parseV3(state)
        Input input = new StringInput("event-replay", state);
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult result = KafkaSqlParser.parse(List.of(input), args);

        for (Script script : result.scripts()) {
            for (Stmt stmt : script.statements()) {
                if (stmt instanceof CreateStmt create) {
                    return create.decl();
                }
            }
        }

        throw new IllegalStateException(
            "Could not parse DDL state for grammar version " + grammarVersion + ": " + state
        );
    }
}
