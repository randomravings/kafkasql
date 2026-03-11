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
}
