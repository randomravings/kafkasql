package kafkasql.runtime;

/**
 * Marker interface for compiled/generated record types from KafkaSQL schemas.
 * <p>
 * All code-generated types (enums, structs, scalars, stream sealed interfaces)
 * implement this interface. The types themselves are dumb data containers — 
 * serialization and compatibility logic lives in external serializers.
 * <p>
 * Contrast with {@link Value} which represents dynamic/runtime values with
 * embedded type metadata, used by the interpreter and query engine.
 * <p>
 * Design:
 * <ul>
 *   <li>Scalars: {@code record MyScalar(int value) implements Record}</li>
 *   <li>Enums: {@code enum MyEnum implements Record { ... }}</li>
 *   <li>Structs: {@code record MyStruct(...) implements Record}</li>
 *   <li>Streams: {@code sealed interface MyStream extends Record permits ...}</li>
 * </ul>
 */
public interface Record { }
