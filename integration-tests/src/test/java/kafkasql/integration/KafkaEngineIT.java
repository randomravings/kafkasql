package kafkasql.integration;

import kafkasql.persistence.EventLogWriter;
import kafkasql.persistence.ModelStore;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamReader;
import kafkasql.runtime.stream.StreamWriter;
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
import sys.schema.SymbolEventLog;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests exercising the full KafkaSQL stack against a real Kafka cluster.
 * <p>
 * Uses Testcontainers to spin up an Apache Kafka broker.
 * Tests cover three scenarios:
 * <ol>
 *   <li><b>Schema persistence</b> — DDL creates events in the SymbolEventLog topic</li>
 *   <li><b>Schema recovery</b> — fresh engine restores schema from topic, accepts DML</li>
 *   <li><b>Data plane</b> — WRITE produces to Kafka, READ consumes back</li>
 * </ol>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaEngineIT {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    private static String bootstrapServers;

    @BeforeAll
    static void init() {
        bootstrapServers = kafka.getBootstrapServers();
        System.out.println("Kafka bootstrap: " + bootstrapServers);
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
     * Wires up everything: ModelStore backed by Kafka SymbolEventLog
     * connected to a KafkaEngine.
     */
    private Harness wireUp() throws Exception {
        // Event log writer (SymbolEventLog topic)
        KafkaProducer<byte[], byte[]> logProducer = newProducer();
        StreamWriter<SymbolEventLog> logWriter = SymbolEventLog.writer(logProducer);
        EventLogWriter eventLogWriter = new EventLogWriter(logWriter, "integration-test");
        ModelStore store = new ModelStore(eventLogWriter);

        // Engine
        KafkaEngine engine = new KafkaEngine(bootstrapServers);
        engine.setSymbolTable(store.symbols());
        engine.setModelChangeListener(store::onCreated);
        engine.setModelDropListener(store::onDropped);

        return new Harness(engine, store, logProducer);
    }

    private record Harness(
        KafkaEngine engine,
        ModelStore store,
        KafkaProducer<byte[], byte[]> logProducer
    ) implements AutoCloseable {
        @Override
        public void close() {
            engine.close();
            logProducer.close();
        }
    }

    // ====================================================================
    // Test 1: Schema creation → events persisted to Kafka
    // ====================================================================

    @Test
    @Order(1)
    void createSchema_persistsEventsToKafka() throws Exception {
        try (var h = wireUp()) {
            // Execute DDL — this should produce events to SymbolEventLog topic
            h.engine().execute("""
                CREATE CONTEXT shop;
                USE CONTEXT shop;

                CREATE TYPE Product AS STRUCT (
                    ProductId INT32,
                    Name STRING,
                    Price INT64
                );

                CREATE STREAM ProductCatalog (
                    TYPE Product AS shop.Product
                );
                """);

            // Verify symbols are in the store
            assertTrue(h.store().symbols().hasKey(Name.of("shop")));
            assertTrue(h.store().symbols().hasKey(Name.of("shop.Product")));
            assertTrue(h.store().symbols().hasKey(Name.of("shop.ProductCatalog")));

            // Verify events actually landed in Kafka by reading the topic
            try (KafkaConsumer<byte[], byte[]> consumer = newConsumer("verify-events")) {
                StreamReader<SymbolEventLog> reader = SymbolEventLog.reader(consumer);

                int count = 0;
                int emptyReads = 0;
                while (emptyReads < 5) {
                    SymbolEventLog event = reader.read();
                    if (event == null) {
                        emptyReads++;
                    } else {
                        count++;
                        emptyReads = 0;
                    }
                }
                assertEquals(3, count, "Should have 3 events (context + type + stream)");
            }
        }
    }

    // ====================================================================
    // Test 2: Schema recovery — fresh engine loads schema from Kafka
    // ====================================================================

    @Test
    @Order(2)
    void recoverSchema_fromKafkaTopic() throws Exception {
        // First, populate the schema (same as test 1, but in its own session)
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT order;
                USE CONTEXT order;
                CREATE TYPE OrderLine AS STRUCT (
                    LineId INT32,
                    ProductName STRING,
                    Quantity INT32
                );
                CREATE STREAM OrderEvents (
                    TYPE OrderLine AS order.OrderLine
                );
                """);
        }

        // Now create a FRESH engine + store and load from Kafka
        KafkaProducer<byte[], byte[]> logProducer = newProducer();
        StreamWriter<SymbolEventLog> logWriter = SymbolEventLog.writer(logProducer);
        EventLogWriter eventLogWriter = new EventLogWriter(logWriter, "integration-test-restore");
        ModelStore restoredStore = new ModelStore(eventLogWriter);

        // Load state from SymbolEventLog topic
        try (KafkaConsumer<byte[], byte[]> consumer = newConsumer("restore-schema")) {
            StreamReader<SymbolEventLog> reader = SymbolEventLog.reader(consumer);
            int replayed = restoredStore.load(reader);
            assertTrue(replayed > 0, "Should replay at least some events");
        }

        // Wire restored store into a fresh engine
        KafkaEngine engine2 = new KafkaEngine(bootstrapServers);
        engine2.setSymbolTable(restoredStore.symbols());
        engine2.setModelChangeListener(restoredStore::onCreated);

        try {
            // The restored engine should accept DML referencing the persisted schema
            // (the "order" context and types should be available)
            assertTrue(restoredStore.symbols().hasKey(Name.of("order")));
            assertTrue(restoredStore.symbols().hasKey(Name.of("order.OrderLine")));
            assertTrue(restoredStore.symbols().hasKey(Name.of("order.OrderEvents")));

            // Execute DML against restored schema
            engine2.execute("""
                WRITE TO order.OrderEvents
                TYPE OrderLine
                VALUES({LineId: 1, ProductName: 'Widget', Quantity: 10});
                """);
        } finally {
            engine2.close();
            logProducer.close();
        }
    }

    // ====================================================================
    // Test 3: Full data plane — WRITE then READ via Kafka
    // ====================================================================

    @Test
    @Order(3)
    void writeAndRead_throughKafka() throws Exception {
        try (var h = wireUp()) {
            // Define schema + write data
            h.engine().execute("""
                CREATE CONTEXT demo;
                USE CONTEXT demo;

                CREATE TYPE Sensor AS STRUCT (
                    SensorId INT32,
                    Location STRING,
                    Temperature INT32
                );

                CREATE STREAM SensorReadings (
                    TYPE Sensor AS demo.Sensor
                );

                WRITE TO demo.SensorReadings
                TYPE Sensor
                VALUES(
                    {SensorId: 1, Location: 'Building A', Temperature: 22},
                    {SensorId: 2, Location: 'Building B', Temperature: 19}
                );
                """);

            // READ back from Kafka
            h.engine().execute("""
                READ FROM demo.SensorReadings
                TYPE Sensor *;
                """);

            var results = h.engine().getLastQueryResult();
            assertEquals(2, results.size(), "Should read 2 sensor records back");

            // Verify field values
            var first = results.get(0).value().fields();
            assertEquals(1, first.get("SensorId"));
            assertEquals("Building A", first.get("Location"));
            assertEquals(22, first.get("Temperature"));

            var second = results.get(1).value().fields();
            assertEquals(2, second.get("SensorId"));
            assertEquals("Building B", second.get("Location"));
            assertEquals(19, second.get("Temperature"));
        }
    }

    // ====================================================================
    // Test 4: Incremental schema + data after restore
    // ====================================================================

    @Test
    @Order(4)
    void incrementalSchemaAndData_afterRestore() throws Exception {
        // Phase 1: Create initial schema
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT warehouse;
                USE CONTEXT warehouse;

                CREATE TYPE Item AS STRUCT (
                    Sku STRING,
                    Name STRING,
                    Qty INT32
                );

                CREATE STREAM Inventory (
                    TYPE Item AS warehouse.Item
                );
                """);
        }

        // Phase 2: Restore and extend the schema
        KafkaProducer<byte[], byte[]> logProducer = newProducer();
        StreamWriter<SymbolEventLog> logWriter = SymbolEventLog.writer(logProducer);
        EventLogWriter eventLogWriter = new EventLogWriter(logWriter, "integration-test-incr");
        ModelStore restoredStore = new ModelStore(eventLogWriter);

        try (KafkaConsumer<byte[], byte[]> consumer = newConsumer("restore-incr")) {
            StreamReader<SymbolEventLog> reader = SymbolEventLog.reader(consumer);
            restoredStore.load(reader);
        }

        KafkaEngine engine2 = new KafkaEngine(bootstrapServers);
        engine2.setSymbolTable(restoredStore.symbols());
        engine2.setModelChangeListener(restoredStore::onCreated);

        try {
            // Add a new type and stream that references existing context
            engine2.execute("""
                USE CONTEXT warehouse;

                CREATE TYPE Shipment AS STRUCT (
                    ShipmentId INT32,
                    Destination STRING
                );

                CREATE STREAM ShipmentLog (
                    TYPE Shipment AS warehouse.Shipment
                );
                """);

            // Verify new symbols exist
            assertTrue(restoredStore.symbols().hasKey(Name.of("warehouse.Shipment")));
            assertTrue(restoredStore.symbols().hasKey(Name.of("warehouse.ShipmentLog")));

            // Write to both old and new streams
            engine2.execute("""
                WRITE TO warehouse.Inventory
                TYPE Item
                VALUES({Sku: 'ABC-123', Name: 'Gadget', Qty: 50});
                """);

            engine2.execute("""
                WRITE TO warehouse.ShipmentLog
                TYPE Shipment
                VALUES({ShipmentId: 1, Destination: 'NYC'});
                """);

            // Read back from both streams
            engine2.execute("READ FROM warehouse.Inventory TYPE Item *;");
            var inventory = engine2.getLastQueryResult();
            assertFalse(inventory.isEmpty(), "Inventory should have records");
            assertEquals("ABC-123", inventory.get(0).value().fields().get("Sku"));

            engine2.execute("READ FROM warehouse.ShipmentLog TYPE Shipment *;");
            var shipments = engine2.getLastQueryResult();
            assertFalse(shipments.isEmpty(), "ShipmentLog should have records");
            assertEquals(1, shipments.get(0).value().fields().get("ShipmentId"));
        } finally {
            engine2.close();
            logProducer.close();
        }
    }

    // ====================================================================
    // Test 5: DROP STREAM succeeds
    // ====================================================================

    @Test
    @Order(5)
    void dropStream_succeeds() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT droptest;
                USE CONTEXT droptest;

                CREATE TYPE Metric AS STRUCT (
                    Name STRING,
                    Value INT32
                );

                CREATE STREAM MetricLog (
                    TYPE Metric AS droptest.Metric
                );
                """);

            assertTrue(h.store().symbols().hasKey(Name.of("droptest.MetricLog")));

            h.engine().execute("DROP STREAM droptest.MetricLog;");

            assertFalse(h.store().symbols().hasKey(Name.of("droptest.MetricLog")),
                "Stream should be removed from symbol table after DROP");
            // Type should still exist
            assertTrue(h.store().symbols().hasKey(Name.of("droptest.Metric")));
        }
    }

    // ====================================================================
    // Test 6: DROP TYPE succeeds when no dependents
    // ====================================================================

    @Test
    @Order(6)
    void dropType_succeedsWhenNoDependents() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT droptest2;
                USE CONTEXT droptest2;

                CREATE TYPE Unused AS STRUCT (
                    Id INT32
                );
                """);

            assertTrue(h.store().symbols().hasKey(Name.of("droptest2.Unused")));

            h.engine().execute("DROP TYPE droptest2.Unused;");

            assertFalse(h.store().symbols().hasKey(Name.of("droptest2.Unused")),
                "Type should be removed from symbol table after DROP");
        }
    }

    // ====================================================================
    // Test 7: DROP TYPE fails when referenced by a stream
    // ====================================================================

    @Test
    @Order(7)
    void dropType_failsWhenReferencedByStream() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT droptest3;
                USE CONTEXT droptest3;

                CREATE TYPE Order AS STRUCT (
                    OrderId INT32,
                    Amount INT64
                );

                CREATE STREAM OrderEvents (
                    TYPE Order AS droptest3.Order
                );
                """);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                h.engine().execute("DROP TYPE droptest3.Order;"));
            assertTrue(ex.getMessage().contains("referenced by"),
                "Error should mention the dependent: " + ex.getMessage());

            // Type should still exist
            assertTrue(h.store().symbols().hasKey(Name.of("droptest3.Order")));
        }
    }

    // ====================================================================
    // Test 8: DROP CONTEXT fails when non-empty
    // ====================================================================

    @Test
    @Order(8)
    void dropContext_failsWhenNonEmpty() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT droptest4;
                USE CONTEXT droptest4;

                CREATE TYPE Widget AS STRUCT (
                    Id INT32
                );
                """);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                h.engine().execute("DROP CONTEXT droptest4;"));
            assertTrue(ex.getMessage().contains("contains"),
                "Error should mention dependents: " + ex.getMessage());

            // Context should still exist
            assertTrue(h.store().symbols().hasKey(Name.of("droptest4")));
        }
    }

    // ====================================================================
    // Test 9: DROP CONTEXT succeeds when empty
    // ====================================================================

    @Test
    @Order(9)
    void dropContext_succeedsWhenEmpty() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("CREATE CONTEXT emptyctx;");

            assertTrue(h.store().symbols().hasKey(Name.of("emptyctx")));

            h.engine().execute("DROP CONTEXT emptyctx;");

            assertFalse(h.store().symbols().hasKey(Name.of("emptyctx")),
                "Context should be removed from symbol table after DROP");
        }
    }

    // ====================================================================
    // Test 10: ALTER TYPE ADD FIELD
    // ====================================================================

    @Test
    @Order(10)
    void alterType_addField() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT altertest;
                USE CONTEXT altertest;

                CREATE TYPE Event AS STRUCT (
                    EventId INT32,
                    Name STRING
                );

                CREATE STREAM EventLog (
                    TYPE Event AS altertest.Event
                );
                """);

            // ALTER TYPE to add a field — should not throw
            assertDoesNotThrow(() ->
                h.engine().execute("ALTER TYPE altertest.Event ADD CreatedAt INT64;"));
        }
    }

    // ====================================================================
    // Test 11: DROP unknown object fails
    // ====================================================================

    @Test
    @Order(11)
    void dropUnknown_fails() throws Exception {
        try (var h = wireUp()) {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                h.engine().execute("DROP TYPE nocontext.NoType;"));
            assertTrue(ex.getMessage().contains("unknown") || ex.getMessage().contains("Unknown"),
                "Error should mention unknown object: " + ex.getMessage());
        }
    }

    // ====================================================================
    // Test 12: DROP STREAM then re-create
    // ====================================================================

    @Test
    @Order(12)
    void dropStream_thenReCreate() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT lifecycle;
                USE CONTEXT lifecycle;

                CREATE TYPE Msg AS STRUCT (
                    Body STRING
                );

                CREATE STREAM MsgLog (
                    TYPE Msg AS lifecycle.Msg
                );
                """);

            assertTrue(h.store().symbols().hasKey(Name.of("lifecycle.MsgLog")));

            h.engine().execute("DROP STREAM lifecycle.MsgLog;");
            assertFalse(h.store().symbols().hasKey(Name.of("lifecycle.MsgLog")));

            // Re-create the same stream
            h.engine().execute("""
                USE CONTEXT lifecycle;
                CREATE STREAM MsgLog (
                    TYPE Msg AS lifecycle.Msg
                );
                """);

            assertTrue(h.store().symbols().hasKey(Name.of("lifecycle.MsgLog")),
                "Stream should be re-created after DROP");
        }
    }

    // ====================================================================
    // Test 13: ALTER TYPE ADD FIELD — write with new field, read fills default
    // ====================================================================

    @Test
    @Order(13)
    void alterTypeAddField_writeAndReadResolution() throws Exception {
        try (var h = wireUp()) {
            // Phase 1: Create schema and write a record with original schema
            h.engine().execute("""
                CREATE CONTEXT evolve;
                USE CONTEXT evolve;

                CREATE TYPE Customer AS STRUCT (
                    Id INT32,
                    Name STRING
                );

                CREATE STREAM Customers (
                    TYPE Customer AS evolve.Customer
                );

                WRITE TO evolve.Customers
                TYPE Customer
                VALUES({Id: 1, Name: 'Alice'});
                """);

            // Phase 2: ALTER TYPE to add a new nullable field
            h.engine().execute("""
                ALTER TYPE evolve.Customer ADD Email STRING NULL;
                """);

            // Write a record using the new schema (includes Email)
            h.engine().execute("""
                WRITE TO evolve.Customers
                TYPE Customer
                VALUES({Id: 2, Name: 'Bob', Email: 'bob@example.com'});
                """);

            // Phase 3: READ — old record should have Email=null, new record has Email
            h.engine().execute("READ FROM evolve.Customers TYPE Customer *;");
            var results = h.engine().getLastQueryResult();
            assertEquals(2, results.size(), "Should have 2 records");

            // First record (written before ALTER) — Email should be null
            var rec1 = results.get(0).value().fields();
            assertEquals(1, rec1.get("Id"));
            assertEquals("Alice", rec1.get("Name"));
            assertNull(rec1.get("Email"), "Email should be null for old record");

            // Second record (written after ALTER) — Email should be present
            var rec2 = results.get(1).value().fields();
            assertEquals(2, rec2.get("Id"));
            assertEquals("Bob", rec2.get("Name"));
            assertEquals("bob@example.com", rec2.get("Email"));
        }
    }

    // ====================================================================
    // Test 14: ALTER TYPE ADD FIELD with DEFAULT — read fills default
    // ====================================================================

    @Test
    @Order(14)
    void alterTypeAddFieldWithDefault_readResolution() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT evolve2;
                USE CONTEXT evolve2;

                CREATE TYPE Sensor AS STRUCT (
                    Id INT32,
                    Value INT64
                );

                CREATE STREAM Readings (
                    TYPE Sensor AS evolve2.Sensor
                );

                WRITE TO evolve2.Readings
                TYPE Sensor
                VALUES({Id: 1, Value: 100});
                """);

            // Add a field with a default value
            h.engine().execute("""
                ALTER TYPE evolve2.Sensor ADD Unit STRING NULL DEFAULT 'celsius';
                """);

            // Write a record without specifying Unit — should get the default
            h.engine().execute("""
                WRITE TO evolve2.Readings
                TYPE Sensor
                VALUES({Id: 2, Value: 200});
                """);

            // Read back — old record gets default, new record gets default too
            h.engine().execute("READ FROM evolve2.Readings TYPE Sensor *;");
            var results = h.engine().getLastQueryResult();
            assertEquals(2, results.size());

            // Old record — Unit filled from default
            assertEquals("celsius", results.get(0).value().fields().get("Unit"));

            // New record — Unit filled from default (was omitted in VALUES)
            assertEquals("celsius", results.get(1).value().fields().get("Unit"));
        }
    }

    // ====================================================================
    // Test 15: ALTER TYPE DROP FIELD — cannot write to dropped field
    // ====================================================================

    @Test
    @Order(15)
    void alterTypeDropField_rejectsWrite() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT evolve3;
                USE CONTEXT evolve3;

                CREATE TYPE Event AS STRUCT (
                    Id INT32,
                    Payload STRING,
                    Debug STRING NULL
                );

                CREATE STREAM EventLog (
                    TYPE Event AS evolve3.Event
                );
                """);

            // Drop the Debug field
            h.engine().execute("""
                ALTER TYPE evolve3.Event DROP Debug;
                """);

            // Attempt to write to dropped field — should fail
            var ex = assertThrows(RuntimeException.class, () ->
                h.engine().execute("""
                    WRITE TO evolve3.EventLog
                    TYPE Event
                    VALUES({Id: 1, Payload: 'test', Debug: 'debug info'});
                    """));
            assertTrue(
                ex.getMessage().contains("dropped") || ex.getMessage().contains("Cannot write"),
                "Error should mention dropped field: " + ex.getMessage());
        }
    }

    // ====================================================================
    // Test 16: ALTER TYPE DROP FIELD — old records still readable
    // ====================================================================

    @Test
    @Order(16)
    void alterTypeDropField_oldRecordsStillReadable() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT evolve4;
                USE CONTEXT evolve4;

                CREATE TYPE Metric AS STRUCT (
                    Name STRING,
                    Value INT32,
                    Tag STRING NULL
                );

                CREATE STREAM Metrics (
                    TYPE Metric AS evolve4.Metric
                );

                WRITE TO evolve4.Metrics
                TYPE Metric
                VALUES({Name: 'cpu', Value: 80, Tag: 'prod'});
                """);

            // Read before DROP — Tag is still active, wire value visible
            h.engine().execute("READ FROM evolve4.Metrics TYPE Metric *;");
            var before = h.engine().getLastQueryResult();
            assertEquals(1, before.size());
            assertEquals("prod", before.get(0).value().fields().get("Tag"));

            // Drop the Tag field
            h.engine().execute("""
                ALTER TYPE evolve4.Metric DROP Tag;
                """);

            // Write a new record (without Tag, which is now dropped)
            h.engine().execute("""
                WRITE TO evolve4.Metrics
                TYPE Metric
                VALUES({Name: 'mem', Value: 60});
                """);

            // Read back — both records get null for dropped Tag field
            h.engine().execute("READ FROM evolve4.Metrics TYPE Metric *;");
            var results = h.engine().getLastQueryResult();
            assertEquals(2, results.size());

            // Old record — Tag is dropped, value defaults to null
            var rec1 = results.get(0).value().fields();
            assertEquals("cpu", rec1.get("Name"));
            assertEquals(80, rec1.get("Value"));
            assertNull(rec1.get("Tag"));

            // New record — Tag is dropped, value defaults to null
            var rec2 = results.get(1).value().fields();
            assertEquals("mem", rec2.get("Name"));
            assertEquals(60, rec2.get("Value"));
            assertNull(rec2.get("Tag"));
        }
    }

    // ====================================================================
    // Test 17: Schema markers are written on ALTER and skipped on READ
    // ====================================================================

    @Test
    @Order(17)
    void alterType_schemaMarkersAreTransparent() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT evolve5;
                USE CONTEXT evolve5;

                CREATE TYPE Order AS STRUCT (
                    Id INT32,
                    Amount INT64,
                    Notes STRING NULL
                );

                CREATE STREAM Orders (
                    TYPE Order AS evolve5.Order
                );

                WRITE TO evolve5.Orders
                TYPE Order
                VALUES({Id: 1, Amount: 100, Notes: 'rush'});
                """);

            // Read BEFORE the DROP — Notes carries the wire value
            h.engine().execute("READ FROM evolve5.Orders TYPE Order *;");
            var pre = h.engine().getLastQueryResult();
            assertEquals(1, pre.size());
            assertEquals("rush", pre.get(0).value().fields().get("Notes"));

            // ALTER writes a schema marker to the Orders topic
            h.engine().execute("""
                ALTER TYPE evolve5.Order DROP Notes;
                """);

            // Write after marker
            h.engine().execute("""
                WRITE TO evolve5.Orders
                TYPE Order
                VALUES({Id: 2, Amount: 200});
                """);

            // Another ALTER — adds a field, writes another marker
            h.engine().execute("""
                ALTER TYPE evolve5.Order ADD Region STRING NULL DEFAULT 'US';
                """);

            h.engine().execute("""
                WRITE TO evolve5.Orders
                TYPE Order
                VALUES({Id: 3, Amount: 300});
                """);

            // Read back — markers should be invisible, only 3 data records
            h.engine().execute("READ FROM evolve5.Orders TYPE Order *;");
            var results = h.engine().getLastQueryResult();
            assertEquals(3, results.size());

            // Record 1: written before any ALTER
            var r1 = results.get(0).value().fields();
            assertEquals(1, r1.get("Id"));
            assertEquals(100L, r1.get("Amount"));
            assertNull(r1.get("Notes"));               // dropped → null
            assertEquals("US", r1.get("Region"));     // added later → DEFAULT

            // Record 2: written after DROP Notes, before ADD Region
            var r2 = results.get(1).value().fields();
            assertEquals(2, r2.get("Id"));
            assertEquals(200L, r2.get("Amount"));
            assertNull(r2.get("Notes"));               // dropped → null
            assertEquals("US", r2.get("Region"));     // added later → DEFAULT

            // Record 3: written after both ALTERs
            var r3 = results.get(2).value().fields();
            assertEquals(3, r3.get("Id"));
            assertEquals(300L, r3.get("Amount"));
            assertNull(r3.get("Notes"));               // dropped → null
            assertEquals("US", r3.get("Region"));     // DEFAULT
        }
    }

    // ====================================================================
    // Test 18: DROP non-nullable field → type default, not null
    // ====================================================================

    @Test
    @Order(18)
    void alterTypeDropNonNullableField_returnsTypeDefault() throws Exception {
        try (var h = wireUp()) {
            h.engine().execute("""
                CREATE CONTEXT evolve6;
                USE CONTEXT evolve6;

                CREATE TYPE Device AS STRUCT (
                    Id INT32,
                    Label STRING,
                    Priority INT32
                );

                CREATE STREAM Devices (
                    TYPE Device AS evolve6.Device
                );

                WRITE TO evolve6.Devices
                TYPE Device
                VALUES({Id: 1, Label: 'sensor-a', Priority: 5});
                """);

            // Read before DROP — Priority carries wire value
            h.engine().execute("READ FROM evolve6.Devices TYPE Device *;");
            var before = h.engine().getLastQueryResult();
            assertEquals(1, before.size());
            assertEquals(5, before.get(0).value().fields().get("Priority"));
            assertEquals("sensor-a", before.get(0).value().fields().get("Label"));

            // Drop non-nullable fields
            h.engine().execute("""
                ALTER TYPE evolve6.Device DROP Priority;
                ALTER TYPE evolve6.Device DROP Label;
                """);

            h.engine().execute("""
                WRITE TO evolve6.Devices
                TYPE Device
                VALUES({Id: 2});
                """);

            // Read after DROP — non-nullable → type default, not null
            h.engine().execute("READ FROM evolve6.Devices TYPE Device *;");
            var results = h.engine().getLastQueryResult();
            assertEquals(2, results.size());

            // Old record — Priority was 5 on wire, but now dropped (non-nullable INT32 → 0)
            var r1 = results.get(0).value().fields();
            assertEquals(1, r1.get("Id"));
            assertEquals("", r1.get("Label"));        // non-nullable STRING → ""
            assertEquals(0, r1.get("Priority"));      // non-nullable INT32 → 0

            // New record — same defaults
            var r2 = results.get(1).value().fields();
            assertEquals(2, r2.get("Id"));
            assertEquals("", r2.get("Label"));
            assertEquals(0, r2.get("Priority"));
        }
    }
}
