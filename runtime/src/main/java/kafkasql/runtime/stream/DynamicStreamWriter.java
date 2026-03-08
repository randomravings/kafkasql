package kafkasql.runtime.stream;

import kafkasql.runtime.value.StructValue;

import java.util.List;

/**
 * Dynamic stream writer for ad-hoc data.
 * 
 * <p>Dynamic stream writers allow writing StructValue messages to a stream without
 * compile-time type definitions. The writer validates messages against a list of
 * allowed struct types.
 * 
 * <p>Each dynamic stream writer has a list of allowed StructValue types that it accepts.
 * This provides runtime type safety for write operations.
 * 
 * <p><b>Example usage:</b>
 * <pre>{@code
 * // Create a dynamic stream writer
 * DynamicStreamWriter writer = ...;
 * 
 * // Allowed types defined at creation
 * List<StructValue> allowedTypes = writer.allowedTypes();
 * 
 * // Write messages
 * StructValue message = new StructValue("CustomerCreated", fields);
 * writer.write(message);
 * }</pre>
 * 
 * <p>This approach provides:
 * <ul>
 *   <li>Flexibility for dynamic data generation</li>
 *   <li>Runtime type validation</li>
 *   <li>Suitable for scripting and dynamic scenarios</li>
 *   <li>Can accept any StructValue matching allowed types</li>
 * </ul>
 */
public interface DynamicStreamWriter extends StreamWriter<StructValue> {
    
    /**
     * Returns the list of allowed struct types for messages in this stream.
     * 
     * <p>Messages written to this stream must match one of these allowed types,
     * otherwise the write operation should fail with a type validation error.
     * 
     * @return Immutable list of allowed StructValue types
     */
    List<StructValue> allowedTypes();
}
