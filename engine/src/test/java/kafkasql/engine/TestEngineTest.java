package kafkasql.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kafkasql.engine.impl.TestEngine;
import kafkasql.runtime.Name;

/**
 * Basic tests for TestEngine - verifying it can parse, bind, and store WRITE statements.
 */
class TestEngineTest {
    
    private TestEngine engine;
    
    @BeforeEach
    void setUp() {
        engine = new TestEngine();
    }
    
    @Test
    void testExecuteSimpleScript() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING
            );
            CREATE STREAM CustomerEvents (
            TYPE Customer AS test.Customer
            );
            """;
        
        assertDoesNotThrow(() -> engine.execute(script), 
            "Script should execute without errors");
    }
    
    @Test
    void testWriteStatement() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING NULL
            );
            
            CREATE STREAM CustomerEvents (
            TYPE Customer AS test.Customer
            );
            
            WRITE TO test.CustomerEvents
            TYPE Customer
            VALUES(@{Id: 1});
            """;
        
        engine.execute(script);
        
        // Verify stream was created and has data
        Name streamName = Name.of("test", "CustomerEvents");
        assertTrue(engine.getStreamNames().contains(streamName));
        
        var records = engine.getStream(streamName);
        assertEquals(1, records.size(), "Should have 1 record in stream");
        
        var record = records.get(0);
        assertEquals("Customer", record.typeName());
        assertNotNull(record.value());
        
        // Inspect the actual struct value
        var structValue = record.value();
        var fields = structValue.fields();
        
        assertEquals(1, fields.size(), "Should have 1 field (nullable Name omitted)");
        assertEquals(1, fields.get("Id"), "Id should be 1");
        assertFalse(fields.containsKey("Name"), "Name should not be present (nullable and not provided)");
        
        System.out.println("Generated StructValue: " + structValue);
    }
    
    @Test
    void testMultipleWrites() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING
            );
            
            CREATE STREAM CustomerEvents (
            TYPE Customer AS test.Customer
            );
            
            WRITE TO test.CustomerEvents
            TYPE Customer
            VALUES(
                @{Id: 1, Name: 'Alice'},
                @{Id: 2, Name: 'Bob'}
            );
            
            WRITE TO test.CustomerEvents
            TYPE Customer
            VALUES(@{Id: 3, Name: 'Charlie'});
            """;
        
        engine.execute(script);
        
        Name streamName = Name.of("test", "CustomerEvents");
        var records = engine.getStream(streamName);
        
        assertEquals(3, records.size(), "Should have 3 records total");
        
        // Verify each record's data
        var record0 = records.get(0);
        assertEquals(1, record0.value().get("Id"));
        assertEquals("Alice", record0.value().get("Name"));
        
        var record1 = records.get(1);
        assertEquals(2, record1.value().get("Id"));
        assertEquals("Bob", record1.value().get("Name"));
        
        var record2 = records.get(2);
        assertEquals(3, record2.value().get("Id"));
        assertEquals("Charlie", record2.value().get("Name"));
        
        System.out.println("All records:");
        records.forEach(r -> System.out.println("  " + r.value()));
    }
    
    @Test
    void testReset() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            CREATE TYPE Customer AS STRUCT (Id INT32);
            CREATE STREAM CustomerEvents (TYPE Customer AS test.Customer);
            WRITE TO test.CustomerEvents TYPE Customer VALUES(@{Id: 1});
            """;
        
        engine.execute(script);
        assertFalse(engine.getStreamNames().isEmpty());
        
        engine.reset();
        assertTrue(engine.getStreamNames().isEmpty(), "Streams should be cleared after reset");
    }
    
    @Test
    void testComplexTypes() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE Status AS ENUM (
                ACTIVE = 0,
                PENDING = 1,
                DISABLED = 2
            );
            
            CREATE TYPE Address AS STRUCT (
                Street STRING,
                Zip STRING NULL
            );
            
            CREATE TYPE IdOrName AS UNION (
                Id INT32,
                Name STRING
            );
            
            CREATE TYPE Customer AS STRUCT (
                Id INT64,
                Name STRING NULL DEFAULT 'Unknown',
                Tags LIST<STRING>,
                Attrs MAP<STRING, INT32>,
                Status test.Status,
                Address test.Address,
                Identifier test.IdOrName
            );
            
            CREATE STREAM CustomerEvents (
            TYPE Customer AS test.Customer
            );
            
            WRITE TO test.CustomerEvents
            TYPE Customer
            VALUES(@{
                Id: 1001,
                Name: 'Alice',
                Tags: ['vip', 'gold'],
                Attrs: {'level': 5, 'score': 100},
                Status: test.Status::ACTIVE,
                Address: @{Street: '123 Main St', Zip: '12345'},
                Identifier: test.IdOrName$Id(42)
            });
            """;
        
        engine.execute(script);
        
        Name streamName = Name.of("test", "CustomerEvents");
        var records = engine.getStream(streamName);
        
        assertEquals(1, records.size());
        var value = records.get(0).value();
        
        // Check primitive fields
        assertEquals(1001L, value.get("Id"));
        assertEquals("Alice", value.get("Name"));
        
        // Check collection types
        var tags = value.get("Tags");
        assertNotNull(tags, "Tags should not be null");
        System.out.println("Tags: " + tags + " (type: " + tags.getClass().getName() + ")");
        
        var attrs = value.get("Attrs");
        assertNotNull(attrs, "Attrs should not be null");
        System.out.println("Attrs: " + attrs + " (type: " + attrs.getClass().getName() + ")");
        
        // Check enum
        var status = value.get("Status");
        assertNotNull(status, "Status should not be null");
        System.out.println("Status: " + status + " (type: " + status.getClass().getName() + ")");
        
        // Check nested struct
        var address = value.get("Address");
        assertNotNull(address, "Address should not be null");
        System.out.println("Address: " + address + " (type: " + address.getClass().getName() + ")");
        
        // Check union
        var identifier = value.get("Identifier");
        assertNotNull(identifier, "Identifier should not be null");
        System.out.println("Identifier: " + identifier + " (type: " + identifier.getClass().getName() + ")");
        
        System.out.println("\nComplete StructValue: " + value);
    }
    
    @Test
    void testMissingRequiredField() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING,
                Email STRING NULL,
                Score INT32 DEFAULT 0
            );
            
            CREATE STREAM CustomerEvents (
            TYPE Customer AS test.Customer
            );
            
            WRITE TO test.CustomerEvents
            TYPE Customer
            VALUES(@{Id: 1});
            """;
        
        // Should fail - Name is required (not nullable, no default)
        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> engine.execute(script));
        
        assertTrue(ex.getMessage().contains("Required field 'Name' is missing"),
            "Error should mention missing required field 'Name'");
    }
    
    @Test
    void testMissingOptionalFields() {
        String script = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING,
                Email STRING NULL,
                Score INT32 DEFAULT 100
            );
            
            CREATE STREAM CustomerEvents (
            TYPE Customer AS test.Customer
            );
            
            WRITE TO test.CustomerEvents
            TYPE Customer
            VALUES(@{Id: 1, Name: 'Alice'});
            """;
        
        // Should succeed - Email is nullable, Score has default
        assertDoesNotThrow(() -> engine.execute(script));
        
        Name streamName = Name.of("test", "CustomerEvents");
        var records = engine.getStream(streamName);
        
        assertEquals(1, records.size());
        var value = records.get(0).value();
        
        assertEquals(1, value.get("Id"));
        assertEquals("Alice", value.get("Name"));
        // Email and Score are omitted - nullable and has default respectively
        assertFalse(value.fields().containsKey("Email"), 
            "Email should not be in fields map when omitted");
        assertFalse(value.fields().containsKey("Score"), 
            "Score should not be in fields map when omitted");
    }
}
