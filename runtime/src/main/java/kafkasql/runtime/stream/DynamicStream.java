package kafkasql.runtime.stream;

/**
 * Marker interface for dynamic streams.
 * 
 * <p>Dynamic streams are created at runtime, typically from query results where
 * the exact types aren't known at compile time. Instead of sealed interfaces with
 * static nested types, dynamic streams use StructValue to represent messages.
 * 
 * <p>This marker interface has no methods - actual functionality is provided by:
 * <ul>
 *   <li>{@link DynamicStreamReader} - For reading dynamic messages</li>
 *   <li>{@link DynamicStreamWriter} - For writing dynamic messages</li>
 * </ul>
 * 
 * <p>Implementations are in the io package and provide runtime type validation
 * based on allowed StructValue types.
 */
public interface DynamicStream {
    // Marker interface - no methods
}
