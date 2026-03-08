package kafkasql.persistence;

import kafkasql.lang.GrammarVersion;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamWriter;
import sys.schema.EventType;
import sys.schema.SymbolEventLog;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Writes symbol table change events to SymbolEventLog stream.
 * 
 * This is the "write side" of event sourcing - capturing all changes
 * to the symbol table as events in Kafka.
 * 
 * Usage:
 * <pre>
 * var writer = SymbolEventLog.writer(producer);
 * var logWriter = new EventLogWriter(writer, "my-app");
 * 
 * // When creating a type
 * logWriter.writeCreate(name, decl, "CREATE TYPE Foo ...");
 * 
 * // When altering
 * logWriter.writeAlter(name, decl, "ALTER TYPE Foo ...", 2);
 * 
 * // When dropping
 * logWriter.writeDrop(name, "DROP TYPE Foo", 3);
 * </pre>
 */
public class EventLogWriter {
    
    private final StreamWriter<SymbolEventLog> writer;
    private final String source;
    
    /**
     * Creates an event log writer.
     * 
     * @param writer Pre-configured stream writer for SymbolEventLog
     * @param source Source identifier (e.g., "compiler", "my-app")
     */
    public EventLogWriter(StreamWriter<SymbolEventLog> writer, String source) {
        this.writer = writer;
        this.source = source;
    }
    
    /**
     * Writes a CREATE event to the log.
     * 
     * @param objectName Fully qualified name of the created object
     * @param decl The declaration of the created object
     * @param statementText Original DDL statement text
     * @throws Exception if writing fails
     */
    public void writeCreate(Name objectName, Decl decl, String statementText) throws Exception {
        SymbolEventLog.SymbolEvent event = new SymbolEventLog.SymbolEvent(
            UUID.randomUUID(),
            LocalDateTime.now(ZoneOffset.UTC),
            source,
            GrammarVersion.CURRENT,
            EventType.CREATE_STMT,
            objectName.fullName(),
            1,  // CREATE is always version 1
            statementText,
            serializeDecl(decl)
        );
        
        writer.write(event);
    }
    
    /**
     * Writes an ALTER event to the log.
     * 
     * @param objectName Fully qualified name of the altered object
     * @param decl The updated declaration
     * @param statementText Original DDL statement text
     * @param version New version number (must be > previous version)
     * @throws Exception if writing fails
     */
    public void writeAlter(Name objectName, Decl decl, String statementText, int version) throws Exception {
        if (version <= 1) {
            throw new IllegalArgumentException("ALTER version must be > 1, got: " + version);
        }
        
        SymbolEventLog.SymbolEvent event = new SymbolEventLog.SymbolEvent(
            UUID.randomUUID(),
            LocalDateTime.now(ZoneOffset.UTC),
            source,
            GrammarVersion.CURRENT,
            EventType.ALTER_STMT,
            objectName.fullName(),
            version,
            statementText,
            serializeDecl(decl)
        );
        
        writer.write(event);
    }
    
    /**
     * Writes a DROP event to the log.
     * 
     * @param objectName Fully qualified name of the dropped object
     * @param statementText Original DDL statement text
     * @param version New version number (must be > previous version)
     * @throws Exception if writing fails
     */
    public void writeDrop(Name objectName, String statementText, int version) throws Exception {
        if (version <= 1) {
            throw new IllegalArgumentException("DROP version must be > 1, got: " + version);
        }
        
        SymbolEventLog.SymbolEvent event = new SymbolEventLog.SymbolEvent(
            UUID.randomUUID(),
            LocalDateTime.now(ZoneOffset.UTC),
            source,
            GrammarVersion.CURRENT,
            EventType.DROP_STMT,
            objectName.fullName(),
            version,
            statementText,
            null  // No state after DROP
        );
        
        writer.write(event);
    }
    
    /**
     * Flushes any pending writes to ensure durability.
     * 
     * @throws Exception if flushing fails
     */
    public void flush() throws Exception {
        writer.flush();
    }
    
    /**
     * Serializes a Decl to its DDL statement text for storage in event log.
     * This is the "State" field - a full CREATE statement that can be re-parsed
     * to reconstruct the object.
     */
    private String serializeDecl(Decl decl) {
        if (decl == null) {
            return null;
        }
        // TODO: Use proper DDL printer to emit canonical DDL text
        return decl.toString();
    }
}
