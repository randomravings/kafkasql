package kafkasql.runtime.stream;

import kafkasql.runtime.value.StructValue;

import java.util.List;

/**
 * Dynamic stream reader for query results.
 * 
 * <p>Dynamic stream readers are created at runtime, typically from query results where
 * the exact types aren't known at compile time. Instead of sealed interfaces with
 * static nested types, dynamic streams use StructValue to represent messages.
 * 
 * <p>Each dynamic stream reader has a list of allowed StructValue types that it can contain.
 * This list is derived from the query that created the stream, providing runtime
 * type safety without compile-time type information.
 * 
 * <p><b>Example usage:</b>
 * <pre>{@code
 * // Query result creates a dynamic stream reader
 * DynamicStreamReader result = engine.execute(
 *     "READ CustomerEvent(CustomerCreated, CustomerUpdated) WHERE ..."
 * );
 * 
 * // Allowed types derived from query
 * List<StructValue> allowedTypes = result.allowedTypes();
 * 
 * // Read messages
 * StructValue message = result.read();
 * if (message != null) {
 *     String typeName = message.typeName();
 *     Object value = message.get("fieldName");
 * }
 * }</pre>
 * 
 * <p>This approach provides:
 * <ul>
 *   <li>Flexibility for runtime queries and exploration</li>
 *   <li>Type information available via StructValue metadata</li>
 *   <li>Suitable for REPL, scripting, and dynamic scenarios</li>
 *   <li>Can be converted to compiled streams if schemas match</li>
 * </ul>
 */
public interface DynamicStreamReader extends StreamReader<StructValue> {
    
    /**
     * Returns the list of allowed struct types for messages in this stream.
     * 
     * <p>This list is derived from the query that created the stream. For example,
     * if a READ statement specifies certain message types, only those types will
     * appear in this list.
     * 
     * @return Immutable list of allowed StructValue types
     */
    List<StructValue> allowedTypes();
}
