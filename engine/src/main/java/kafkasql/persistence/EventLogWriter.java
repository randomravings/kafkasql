package kafkasql.persistence;

import kafkasql.lang.GrammarVersion;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamWriter;
import sys.schema.EventType;
import sys.schema.SymbolEventLog;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
            statementText  // State = full DDL for replay (same as Delta for CREATE)
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
        writeAlter(objectName, decl, statementText, version, Map.of());
    }
    
    /**
     * Writes an ALTER event to the log with schema-change marker offsets.
     * 
     * @param objectName Fully qualified name of the altered object
     * @param decl The updated declaration
     * @param statementText Original DDL statement text
     * @param version New version number (must be > previous version)
     * @param streamOffsets Map of stream name → (partition → offset) for schema markers
     * @throws Exception if writing fails
     */
    public void writeAlter(Name objectName, Decl decl, String statementText, int version,
                           Map<String, Map<Integer, Long>> streamOffsets) throws Exception {
        if (version <= 1) {
            throw new IllegalArgumentException("ALTER version must be > 1, got: " + version);
        }
        
        String delta = statementText;
        if (streamOffsets != null && !streamOffsets.isEmpty()) {
            delta = statementText + OFFSET_SEPARATOR + encodeOffsets(streamOffsets);
        }
        
        SymbolEventLog.SymbolEvent event = new SymbolEventLog.SymbolEvent(
            UUID.randomUUID(),
            LocalDateTime.now(ZoneOffset.UTC),
            source,
            GrammarVersion.CURRENT,
            EventType.ALTER_STMT,
            objectName.fullName(),
            version,
            delta,
            statementText  // State = full DDL for replay (TODO: DDL printer for ALTER)
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
            ""  // No state after DROP
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
    
    // ========================================================================
    // Stream offset encoding (stored in Delta field for ALTER events)
    // ========================================================================
    
    /** Separator between DDL text and encoded offsets in the Delta field. */
    static final String OFFSET_SEPARATOR = "\0";
    
    /**
     * Encodes stream offsets as a compact string.
     * Format: {@code stream:partition:offset[,stream:partition:offset]*}
     */
    static String encodeOffsets(Map<String, Map<Integer, Long>> streamOffsets) {
        var sb = new StringBuilder();
        boolean first = true;
        for (var streamEntry : streamOffsets.entrySet()) {
            for (var partEntry : streamEntry.getValue().entrySet()) {
                if (!first) sb.append(',');
                sb.append(streamEntry.getKey())
                  .append(':')
                  .append(partEntry.getKey())
                  .append(':')
                  .append(partEntry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }
    
    /**
     * Decodes stream offsets from the compact string format.
     * Returns an empty map if the input is null or empty.
     */
    static Map<String, Map<Integer, Long>> decodeOffsets(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Map.of();
        var result = new java.util.HashMap<String, Map<Integer, Long>>();
        for (String entry : encoded.split(",")) {
            // Format: stream:partition:offset — split from the right
            int lastColon = entry.lastIndexOf(':');
            int secondColon = entry.lastIndexOf(':', lastColon - 1);
            String stream = entry.substring(0, secondColon);
            int partition = Integer.parseInt(entry.substring(secondColon + 1, lastColon));
            long offset = Long.parseLong(entry.substring(lastColon + 1));
            result.computeIfAbsent(stream, k -> new java.util.HashMap<>())
                  .put(partition, offset);
        }
        return result;
    }
    
    /**
     * Extracts the DDL text from a Delta field that may contain encoded offsets.
     */
    static String extractDdl(String delta) {
        int sep = delta.indexOf(OFFSET_SEPARATOR);
        return sep < 0 ? delta : delta.substring(0, sep);
    }
    
    /**
     * Extracts stream offsets from a Delta field, or empty map if none present.
     */
    static Map<String, Map<Integer, Long>> extractOffsets(String delta) {
        int sep = delta.indexOf(OFFSET_SEPARATOR);
        if (sep < 0) return Map.of();
        return decodeOffsets(delta.substring(sep + OFFSET_SEPARATOR.length()));
    }

}
