package kafkasql.integration;

import kafkasql.io.ReadStream;
import kafkasql.io.ValueCodec;
import kafkasql.io.WriteStream;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamReader;
import kafkasql.runtime.stream.StreamWriter;
import kafkasql.runtime.type.*;
import kafkasql.runtime.value.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests exercising ReadStream / WriteStream against a real Kafka broker.
 * <p>
 * Uses ValueCodec as the serialization layer — the same mechanism the generated
 * {@code writeTo} / {@code readFrom} methods delegate to at the field level.
 * This validates the full write → Kafka → read round-trip for every Value type.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StreamReadWriteIT {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    private static String bootstrapServers;

    @BeforeAll
    static void init() {
        bootstrapServers = kafka.getBootstrapServers();
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private KafkaProducer<byte[], byte[]> newProducer() {
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<byte[], byte[]> newConsumer(String groupId) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    /**
     * Creates a WriteStream that serializes a Value via ValueCodec.
     */
    private StreamWriter<Value> valueWriter(String topic, KafkaProducer<byte[], byte[]> producer) {
        return new WriteStream<>(topic, producer, ValueCodec::toByteArray);
    }

    /**
     * Creates a ReadStream that deserializes a Value via ValueCodec.
     */
    private StreamReader<Value> valueReader(String topic, KafkaConsumer<byte[], byte[]> consumer, AnyType type) {
        return new ReadStream<>(topic, consumer, bytes -> ValueCodec.fromByteArray(type, bytes));
    }

    /**
     * Reads all available messages, tolerating a few empty polls before giving up.
     */
    private List<Value> readAll(StreamReader<Value> reader, int expectedCount) throws Exception {
        var results = new ArrayList<Value>();
        int emptyPolls = 0;
        while (results.size() < expectedCount && emptyPolls < 10) {
            Value v = reader.read();
            if (v == null) {
                emptyPolls++;
            } else {
                results.add(v);
                emptyPolls = 0;
            }
        }
        return results;
    }

    // ====================================================================
    // Test 1: Struct round-trip through Kafka
    // ====================================================================

    @Test
    @Order(1)
    void structValue_writeAndRead() throws Exception {
        var type = buildSensorStruct();
        String topic = "stream-it-struct";

        // Write
        try (var producer = newProducer()) {
            var writer = valueWriter(topic, producer);

            var fields1 = new LinkedHashMap<String, Object>();
            fields1.put("SensorId", 1);
            fields1.put("Location", "Building A");
            fields1.put("Temperature", 22);
            writer.write(new StructValue(type, fields1));

            var fields2 = new LinkedHashMap<String, Object>();
            fields2.put("SensorId", 2);
            fields2.put("Location", "Building B");
            fields2.put("Temperature", 19);
            writer.write(new StructValue(type, fields2));

            writer.flush();
        }

        // Read
        try (var consumer = newConsumer("struct-read")) {
            var reader = valueReader(topic, consumer, type);
            var results = readAll(reader, 2);

            assertEquals(2, results.size());

            var s1 = (StructValue) results.get(0);
            assertEquals(1, s1.get("SensorId"));
            assertEquals("Building A", s1.get("Location"));
            assertEquals(22, s1.get("Temperature"));

            var s2 = (StructValue) results.get(1);
            assertEquals(2, s2.get("SensorId"));
            assertEquals("Building B", s2.get("Location"));
            assertEquals(19, s2.get("Temperature"));
        }
    }

    // ====================================================================
    // Test 2: Enum round-trip through Kafka
    // ====================================================================

    @Test
    @Order(2)
    void enumValue_writeAndRead() throws Exception {
        var type = buildStatusEnum();
        String topic = "stream-it-enum";

        try (var producer = newProducer()) {
            var writer = valueWriter(topic, producer);
            writer.write(new EnumValue(type, type.symbols().get(0))); // PENDING
            writer.write(new EnumValue(type, type.symbols().get(1))); // ACTIVE
            writer.write(new EnumValue(type, type.symbols().get(2))); // DISABLED
            writer.flush();
        }

        try (var consumer = newConsumer("enum-read")) {
            var reader = valueReader(topic, consumer, type);
            var results = readAll(reader, 3);

            assertEquals(3, results.size());
            assertEquals("PENDING", ((EnumValue) results.get(0)).symbolName());
            assertEquals("ACTIVE", ((EnumValue) results.get(1)).symbolName());
            assertEquals("DISABLED", ((EnumValue) results.get(2)).symbolName());
        }
    }

    // ====================================================================
    // Test 3: Scalar round-trip through Kafka
    // ====================================================================

    @Test
    @Order(3)
    void scalarValue_writeAndRead() throws Exception {
        var type = new ScalarType(Name.of("test", "Amount"), PrimitiveType.int64(),
                Optional.empty(), Optional.empty(), Optional.empty());
        String topic = "stream-it-scalar";

        try (var producer = newProducer()) {
            var writer = valueWriter(topic, producer);
            writer.write(new ScalarValue(type, 100L));
            writer.write(new ScalarValue(type, 999_999L));
            writer.flush();
        }

        try (var consumer = newConsumer("scalar-read")) {
            var reader = valueReader(topic, consumer, type);
            var results = readAll(reader, 2);

            assertEquals(2, results.size());
            assertEquals(100L, ((ScalarValue) results.get(0)).value());
            assertEquals(999_999L, ((ScalarValue) results.get(1)).value());
        }
    }

    // ====================================================================
    // Test 4: Union round-trip through Kafka
    // ====================================================================

    @Test
    @Order(4)
    void unionValue_writeAndRead() throws Exception {
        var members = new LinkedHashMap<String, UnionTypeMember>();
        members.put("Id", new UnionTypeMember("Id", PrimitiveType.int32(), Optional.empty()));
        members.put("Name", new UnionTypeMember("Name", PrimitiveType.string(), Optional.empty()));
        var type = new UnionType(Name.of("test", "IdOrName"), members, Optional.empty());
        String topic = "stream-it-union";

        try (var producer = newProducer()) {
            var writer = valueWriter(topic, producer);
            writer.write(new UnionValue(type, "Id", 42));
            writer.write(new UnionValue(type, "Name", "Alice"));
            writer.flush();
        }

        try (var consumer = newConsumer("union-read")) {
            var reader = valueReader(topic, consumer, type);
            var results = readAll(reader, 2);

            assertEquals(2, results.size());

            var u1 = (UnionValue) results.get(0);
            assertEquals("Id", u1.memberName());
            assertEquals(42, u1.value());

            var u2 = (UnionValue) results.get(1);
            assertEquals("Name", u2.memberName());
            assertEquals("Alice", u2.value());
        }
    }

    // ====================================================================
    // Test 5: Nested struct (struct with enum + nullable fields) 
    // ====================================================================

    @Test
    @Order(5)
    void nestedStruct_writeAndRead() throws Exception {
        var statusEnum = buildStatusEnum();
        var structFields = new LinkedHashMap<String, StructTypeField>();
        structFields.put("Name", new StructTypeField("Name", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        structFields.put("Status", new StructTypeField("Status", statusEnum, false, Optional.empty(), Optional.empty()));
        structFields.put("Notes", new StructTypeField("Notes", PrimitiveType.string(), true, Optional.empty(), Optional.empty()));
        var type = new StructType(Name.of("test", "Employee"), structFields, List.of(), Optional.empty());
        String topic = "stream-it-nested";

        try (var producer = newProducer()) {
            var writer = valueWriter(topic, producer);

            // Record with non-null optional field
            var fields1 = new LinkedHashMap<String, Object>();
            fields1.put("Name", "Alice");
            fields1.put("Status", new EnumValue(statusEnum, statusEnum.symbols().get(1))); // ACTIVE
            fields1.put("Notes", "Senior engineer");
            writer.write(new StructValue(type, fields1));

            // Record with null optional field
            var fields2 = new LinkedHashMap<String, Object>();
            fields2.put("Name", "Bob");
            fields2.put("Status", new EnumValue(statusEnum, statusEnum.symbols().get(0))); // PENDING
            fields2.put("Notes", null);
            writer.write(new StructValue(type, fields2));

            writer.flush();
        }

        try (var consumer = newConsumer("nested-read")) {
            var reader = valueReader(topic, consumer, type);
            var results = readAll(reader, 2);

            assertEquals(2, results.size());

            var r1 = (StructValue) results.get(0);
            assertEquals("Alice", r1.get("Name"));
            assertEquals("ACTIVE", ((EnumValue) r1.get("Status")).symbolName());
            assertEquals("Senior engineer", r1.get("Notes"));

            var r2 = (StructValue) results.get(1);
            assertEquals("Bob", r2.get("Name"));
            assertEquals("PENDING", ((EnumValue) r2.get("Status")).symbolName());
            assertNull(r2.get("Notes"));
        }
    }

    // ====================================================================
    // Test 6: Multiple primitive types in a single struct
    // ====================================================================

    @Test
    @Order(6)
    void widePrimitiveStruct_writeAndRead() throws Exception {
        var structFields = new LinkedHashMap<String, StructTypeField>();
        structFields.put("b", new StructTypeField("b", PrimitiveType.bool(), false, Optional.empty(), Optional.empty()));
        structFields.put("i8", new StructTypeField("i8", PrimitiveType.int8(), false, Optional.empty(), Optional.empty()));
        structFields.put("i16", new StructTypeField("i16", PrimitiveType.int16(), false, Optional.empty(), Optional.empty()));
        structFields.put("i32", new StructTypeField("i32", PrimitiveType.int32(), false, Optional.empty(), Optional.empty()));
        structFields.put("i64", new StructTypeField("i64", PrimitiveType.int64(), false, Optional.empty(), Optional.empty()));
        structFields.put("f32", new StructTypeField("f32", PrimitiveType.float32(), false, Optional.empty(), Optional.empty()));
        structFields.put("f64", new StructTypeField("f64", PrimitiveType.float64(), false, Optional.empty(), Optional.empty()));
        structFields.put("s", new StructTypeField("s", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        structFields.put("bin", new StructTypeField("bin", PrimitiveType.bytes(), false, Optional.empty(), Optional.empty()));
        structFields.put("uid", new StructTypeField("uid", PrimitiveType.uuid(), false, Optional.empty(), Optional.empty()));
        var type = new StructType(Name.of("test", "Wide"), structFields, List.of(), Optional.empty());
        String topic = "stream-it-wide";

        UUID testUuid = UUID.randomUUID();

        try (var producer = newProducer()) {
            var writer = valueWriter(topic, producer);
            var fields = new LinkedHashMap<String, Object>();
            fields.put("b", true);
            fields.put("i8", (byte) 127);
            fields.put("i16", (short) 32000);
            fields.put("i32", 42);
            fields.put("i64", Long.MAX_VALUE);
            fields.put("f32", 3.14f);
            fields.put("f64", 2.718281828);
            fields.put("s", "hello kafka");
            fields.put("bin", new byte[]{0, 1, 2, (byte) 0xFF});
            fields.put("uid", testUuid);
            writer.write(new StructValue(type, fields));
            writer.flush();
        }

        try (var consumer = newConsumer("wide-read")) {
            var reader = valueReader(topic, consumer, type);
            var results = readAll(reader, 1);

            assertEquals(1, results.size());
            var sv = (StructValue) results.get(0);
            assertEquals(true, sv.get("b"));
            assertEquals((byte) 127, sv.get("i8"));
            assertEquals((short) 32000, sv.get("i16"));
            assertEquals(42, sv.get("i32"));
            assertEquals(Long.MAX_VALUE, sv.get("i64"));
            assertEquals(3.14f, sv.get("f32"));
            assertEquals(2.718281828, sv.get("f64"));
            assertEquals("hello kafka", sv.get("s"));
            assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xFF}, (byte[]) sv.get("bin"));
            assertEquals(testUuid, sv.get("uid"));
        }
    }

    // ====================================================================
    // Test 7: Generated SymbolEventLog stream (writeTo/readFrom codegen)
    // ====================================================================

    @Test
    @Order(7)
    void generatedStream_symbolEventLog_writeAndRead() throws Exception {
        String topic = "SymbolEventLog-it";

        // Write using the generated writer factory
        try (var producer = newProducer()) {
            var writer = new WriteStream<sys.schema.SymbolEventLog>(
                topic, producer,
                msg -> {
                    var baos = new ByteArrayOutputStream();
                    msg.writeTo(baos);
                    return baos.toByteArray();
                }
            );

            var event = new sys.schema.SymbolEventLog.SymbolEvent(
                UUID.randomUUID(),
                java.time.LocalDateTime.of(2026, 3, 14, 10, 30, 0),
                "integration-test",
                1,
                sys.schema.EventType.CREATE_STMT,
                "test.MyType",
                1,
                "CREATE TYPE MyType AS SCALAR STRING;",
                "CREATE TYPE MyType AS SCALAR STRING;"
            );
            writer.write(event);
            writer.flush();
        }

        // Read using the generated reader factory
        try (var consumer = newConsumer("gen-stream-read")) {
            var reader = new ReadStream<>(
                topic, consumer,
                bytes -> sys.schema.SymbolEventLog.readFrom(new ByteArrayInputStream(bytes))
            );

            sys.schema.SymbolEventLog result = null;
            int emptyPolls = 0;
            while (result == null && emptyPolls < 10) {
                result = reader.read();
                if (result == null) emptyPolls++;
            }

            assertNotNull(result);
            assertInstanceOf(sys.schema.SymbolEventLog.SymbolEvent.class, result);
            var event = (sys.schema.SymbolEventLog.SymbolEvent) result;
            assertEquals("integration-test", event.Source());
            assertEquals("test.MyType", event.ObjectName());
            assertEquals(1, event.ObjectVersion());
            assertEquals(sys.schema.EventType.CREATE_STMT, event.EventType());
        }
    }

    // ====================================================================
    // Type builders
    // ====================================================================

    private StructType buildSensorStruct() {
        var fields = new LinkedHashMap<String, StructTypeField>();
        fields.put("SensorId", new StructTypeField("SensorId", PrimitiveType.int32(), false, Optional.empty(), Optional.empty()));
        fields.put("Location", new StructTypeField("Location", PrimitiveType.string(), false, Optional.empty(), Optional.empty()));
        fields.put("Temperature", new StructTypeField("Temperature", PrimitiveType.int32(), false, Optional.empty(), Optional.empty()));
        return new StructType(Name.of("test", "Sensor"), fields, List.of(), Optional.empty());
    }

    private EnumType buildStatusEnum() {
        return new EnumType(
            Name.of("test", "Status"),
            PrimitiveType.int32(),
            List.of(
                new EnumTypeSymbol("PENDING", 0, Optional.empty()),
                new EnumTypeSymbol("ACTIVE", 1, Optional.empty()),
                new EnumTypeSymbol("DISABLED", 2, Optional.empty())
            ),
            Optional.empty()
        );
    }
}
