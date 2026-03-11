package kafkasql.persistence;

import kafkasql.engine.KafkaSqlEngine;
import kafkasql.persistence.stream.InMemoryStream;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamWriter;
import kafkasql.runtime.value.StructValue;
import sys.schema.EventType;
import sys.schema.SymbolEventLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for the model persistence lifecycle.
 * <p>
 * Exercises the full flow: engine → mutation detection → event log → replay.
 * Uses in-memory streams so no Kafka infrastructure is needed.
 *
 * <h3>Lifecycle under test</h3>
 * <ol>
 *   <li>Wire ModelStore → Engine</li>
 *   <li>Execute DDL → events captured in event log</li>
 *   <li>Create fresh ModelStore → load from event log → symbols restored</li>
 *   <li>Continue with incremental DDL → only new events appended</li>
 * </ol>
 */
class ModelStoreTest {

    private InMemoryStream<SymbolEventLog> eventLog;
    private KafkaSqlEngine engine;
    private ModelStore store;

    @BeforeEach
    void setUp() {
        eventLog = new InMemoryStream<>("SymbolEventLog");

        // Writer side → EventLogWriter → ModelStore
        StreamWriter<SymbolEventLog> streamWriter = eventLog.writer();
        EventLogWriter logWriter = new EventLogWriter(streamWriter, "test");
        store = new ModelStore(logWriter);

        // Wire store into engine
        engine = new InMemoryEngine();
        engine.setSymbolTable(store.symbols());
        engine.setModelChangeListener(store::onCreated);
    }

    // ====================================================================
    // Phase 1: Schema creation → events persisted
    // ====================================================================

    @Test
    void createContext_persistsOneEvent() {
        engine.execute("CREATE CONTEXT com;");

        assertEquals(1, eventLog.size(), "One event for CREATE CONTEXT");
        assertEvent(eventLog.messages().get(0), EventType.CREATE_STMT, "com");
    }

    @Test
    void createMultipleObjects_persistsAllEvents() {
        engine.execute("""
            CREATE CONTEXT com;
            USE CONTEXT com;

            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING
            );

            CREATE STREAM CustomerEvents (
                TYPE Customer AS com.Customer
            );
            """);

        // 3 objects: context, type, stream
        assertEquals(3, eventLog.size());
        assertEvent(eventLog.messages().get(0), EventType.CREATE_STMT, "com");
        assertEvent(eventLog.messages().get(1), EventType.CREATE_STMT, "com.Customer");
        assertEvent(eventLog.messages().get(2), EventType.CREATE_STMT, "com.CustomerEvents");

        // Symbol table should have all three
        assertTrue(store.symbols().hasKey(Name.of("com")));
        assertTrue(store.symbols().hasKey(Name.of("com.Customer")));
        assertTrue(store.symbols().hasKey(Name.of("com.CustomerEvents")));
    }

    @Test
    void eventState_containsOriginalDdl() {
        engine.execute("""
            CREATE CONTEXT com;
            USE CONTEXT com;
            CREATE TYPE Order AS STRUCT (
                OrderId INT64,
                Quantity INT32
            );
            """);

        // The State field should contain parseable DDL
        assertEquals(2, eventLog.size());
        var typeEvent = asSymbolEvent(eventLog.messages().get(1));
        assertNotNull(typeEvent.State());
        assertTrue(typeEvent.State().contains("CREATE TYPE"),
            "State should contain DDL: " + typeEvent.State());
        assertTrue(typeEvent.State().contains("Order"),
            "State should reference type name: " + typeEvent.State());
    }

    // ====================================================================
    // Phase 2: State recovery from event log
    // ====================================================================

    @Test
    void load_restoresSymbolsFromEventLog() throws Exception {
        // Step 1: Create schema → events flow to event log
        engine.execute("""
            CREATE CONTEXT com;
            USE CONTEXT com;

            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING
            );

            CREATE STREAM CustomerEvents (
                TYPE Customer AS com.Customer
            );
            """);

        assertEquals(3, eventLog.size());

        // Step 2: Fresh store loads from the same event log
        ModelStore restored = new ModelStore();
        int replayed = restored.load(eventLog.reader());

        assertEquals(3, replayed, "Should replay 3 events");

        // Verify every symbol is present
        assertTrue(restored.symbols().hasKey(Name.of("com")));
        assertTrue(restored.symbols().hasKey(Name.of("com.Customer")));
        assertTrue(restored.symbols().hasKey(Name.of("com.CustomerEvents")));
    }

    @Test
    void load_restoredSymbolsAreUsableByEngine() throws Exception {
        // Create and persist schema
        engine.execute("""
            CREATE CONTEXT com;
            USE CONTEXT com;

            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING
            );

            CREATE STREAM CustomerEvents (
                TYPE Customer AS com.Customer
            );
            """);

        // Restore into a fresh engine
        ModelStore restored = new ModelStore();
        restored.load(eventLog.reader());

        InMemoryEngine engine2 = new InMemoryEngine();
        engine2.setSymbolTable(restored.symbols());

        // The restored engine should accept DML referencing the persisted schema
        engine2.execute("""
            WRITE TO com.CustomerEvents
            TYPE Customer
            VALUES({Id: 42, Name: 'Alice'});
            """);

        var records = engine2.getStream(Name.of("com", "CustomerEvents"));
        assertEquals(1, records.size());
        assertEquals(42, records.get(0).value().fields().get("Id"));
    }

    // ====================================================================
    // Phase 3: Incremental mutations after restore
    // ====================================================================

    @Test
    void incrementalDdl_appendsOnlyNewEvents() throws Exception {
        // Initial schema
        engine.execute("""
            CREATE CONTEXT com;
            USE CONTEXT com;
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING
            );
            """);
        assertEquals(2, eventLog.size());

        // Restore into a new engine wired to the SAME event log
        ModelStore restored = new ModelStore(
            new EventLogWriter(eventLog.writer(), "test-restore")
        );
        restored.load(eventLog.reader());

        InMemoryEngine engine2 = new InMemoryEngine();
        engine2.setSymbolTable(restored.symbols());
        engine2.setModelChangeListener(restored::onCreated);

        // Add more DDL
        engine2.execute("""
            USE CONTEXT com;
            CREATE STREAM CustomerEvents (
                TYPE Customer AS com.Customer
            );
            """);

        // Only the new stream should have been appended
        assertEquals(3, eventLog.size());
        assertEvent(eventLog.messages().get(2), EventType.CREATE_STMT, "com.CustomerEvents");
    }

    // ====================================================================
    // Phase 4: Error handling and rollback
    // ====================================================================

    @Test
    void semanticError_doesNotPersistEvents() {
        int beforeSize = eventLog.size();

        // Reference an undefined type → semantic error
        assertThrows(RuntimeException.class, () -> engine.execute("""
            CREATE CONTEXT com;
            USE CONTEXT com;
            CREATE STREAM BadStream (
                TYPE Missing AS com.Undefined
            );
            """));

        // The context CREATE may have been registered before the error,
        // but the engine should rollback ALL new symbols on error
        assertEquals(beforeSize, eventLog.size(),
            "No events should be persisted on semantic error");
    }

    @Test
    void duplicateCreate_isSemanticError() {
        engine.execute("CREATE CONTEXT com;");
        assertEquals(1, eventLog.size());

        // Creating the same context again should fail
        assertThrows(RuntimeException.class, () ->
            engine.execute("CREATE CONTEXT com;"));

        // No additional events for the failed duplicate
        assertEquals(1, eventLog.size());
    }

    // ====================================================================
    // Phase 5: Version tracking
    // ====================================================================

    @Test
    void versionTracking_startsAtOneForCreate() {
        engine.execute("CREATE CONTEXT com;");

        assertEquals(1, store.getVersion(Name.of("com")));
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private void assertEvent(SymbolEventLog event, EventType expectedType, String expectedName) {
        var se = asSymbolEvent(event);
        assertEquals(expectedType, se.EventType());
        assertEquals(expectedName, se.ObjectName());
    }

    private SymbolEventLog.SymbolEvent asSymbolEvent(SymbolEventLog event) {
        assertInstanceOf(SymbolEventLog.SymbolEvent.class, event);
        return (SymbolEventLog.SymbolEvent) event;
    }

    // ====================================================================
    // Minimal in-memory engine for testing
    // ====================================================================

    /**
     * Lightweight KafkaSqlEngine that stores records in memory.
     * Only implements what's needed for persistence lifecycle tests.
     */
    private static final class InMemoryEngine extends KafkaSqlEngine {
        private final Map<Name, List<StreamRecord>> streams = new HashMap<>();

        @Override
        protected void writeRecord(Name streamName, String typeName, StructValue value) {
            streams.computeIfAbsent(streamName, k -> new ArrayList<>())
                .add(new StreamRecord(typeName, value));
        }

        @Override
        protected List<StreamRecord> readRecords(Name streamName) {
            return streams.getOrDefault(streamName, List.of());
        }

        List<StreamRecord> getStream(Name streamName) {
            return Collections.unmodifiableList(
                streams.getOrDefault(streamName, List.of())
            );
        }
    }
}
