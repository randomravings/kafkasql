package kafkasql.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kafkasql.engine.impl.TestEngine;
import kafkasql.runtime.Name;

/**
 * Tests for READ query execution with predefined data fixtures.
 * 
 * This test class sets up streams with test data that can be queried
 * in multiple scenarios without duplicating setup code.
 */
class ReadQueryTest {
    
    private TestEngine engine;
    private String setupScript;
    
    // Stream names for easy reference
    private static final Name EVENTS_STREAM = Name.of("test", "Events");
    
    @BeforeEach
    void setUp() {
        engine = new TestEngine();
        setupTestData();
    }
    
    /**
     * Set up test data across multiple streams.
     * This creates a small e-commerce-like dataset for testing queries.
     */
    private void setupTestData() {
        setupScript = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            -- Customer data
            CREATE TYPE Customer AS STRUCT (
                Id INT32,
                Name STRING,
                Email STRING,
                Age INT32,
                Status STRING
            );
            
            CREATE STREAM Customers (
                TYPE Customer AS test.Customer
            );
            
            WRITE TO test.Customers
            TYPE Customer
            VALUES(
                {Id: 1, Name: 'Alice', Email: 'alice@example.com', Age: 30, Status: 'ACTIVE'},
                {Id: 2, Name: 'Bob', Email: 'bob@example.com', Age: 25, Status: 'ACTIVE'},
                {Id: 3, Name: 'Charlie', Email: 'charlie@example.com', Age: 35, Status: 'INACTIVE'},
                {Id: 4, Name: 'Diana', Email: 'diana@example.com', Age: 28, Status: 'ACTIVE'},
                {Id: 5, Name: 'Eve', Email: 'eve@example.com', Age: 42, Status: 'ACTIVE'}
            );
            
            -- Product data
            CREATE TYPE Product AS STRUCT (
                Id INT32,
                Name STRING,
                Category STRING,
                Price INT32,
                InStock BOOLEAN
            );
            
            CREATE STREAM Products (
                TYPE Product AS test.Product
            );
            
            WRITE TO test.Products
            TYPE Product
            VALUES(
                {Id: 101, Name: 'Widget', Category: 'Tools', Price: 100, InStock: true},
                {Id: 102, Name: 'Gadget', Category: 'Electronics', Price: 250, InStock: true},
                {Id: 103, Name: 'Gizmo', Category: 'Tools', Price: 150, InStock: false},
                {Id: 104, Name: 'Doohickey', Category: 'Home', Price: 75, InStock: true},
                {Id: 105, Name: 'Thingamajig', Category: 'Electronics', Price: 300, InStock: true}
            );
            
            -- Order data (simplified - just customer ID and product ID)
            CREATE TYPE Order AS STRUCT (
                OrderId INT32,
                CustomerId INT32,
                ProductId INT32,
                Quantity INT32,
                Total INT32
            );
            
            CREATE STREAM Orders (
                TYPE Order AS test.Order
            );
            
            WRITE TO test.Orders
            TYPE Order
            VALUES(
                {OrderId: 1001, CustomerId: 1, ProductId: 101, Quantity: 2, Total: 200},
                {OrderId: 1002, CustomerId: 2, ProductId: 102, Quantity: 1, Total: 250},
                {OrderId: 1003, CustomerId: 1, ProductId: 103, Quantity: 1, Total: 150},
                {OrderId: 1004, CustomerId: 4, ProductId: 104, Quantity: 3, Total: 225},
                {OrderId: 1005, CustomerId: 2, ProductId: 105, Quantity: 1, Total: 300},
                {OrderId: 1006, CustomerId: 5, ProductId: 101, Quantity: 1, Total: 100}
            );
            
            -- Multi-type event stream (demonstrates multiple types on same stream)
            CREATE TYPE UserCreated AS STRUCT (
                UserId INT32,
                Username STRING,
                Email STRING
            );
            
            CREATE TYPE UserUpdated AS STRUCT (
                UserId INT32,
                Field STRING,
                OldValue STRING,
                NewValue STRING
            );
            
            CREATE TYPE UserDeleted AS STRUCT (
                UserId INT32,
                Reason STRING
            );
            
            CREATE STREAM Events (
                TYPE UserCreated AS test.UserCreated,
                TYPE UserUpdated AS test.UserUpdated,
                TYPE UserDeleted AS test.UserDeleted
            );
            
            WRITE TO test.Events
            TYPE UserCreated
            VALUES(
                {UserId: 1, Username: 'alice', Email: 'alice@example.com'},
                {UserId: 2, Username: 'bob', Email: 'bob@example.com'}
            );
            
            WRITE TO test.Events
            TYPE UserUpdated
            VALUES(
                {UserId: 1, Field: 'Email', OldValue: 'alice@example.com', NewValue: 'alice.new@example.com'},
                {UserId: 2, Field: 'Username', OldValue: 'bob', NewValue: 'robert'}
            );
            
            WRITE TO test.Events
            TYPE UserDeleted
            VALUES(
                {UserId: 2, Reason: 'Account closed by user'}
            );
            
            -- Key/Value wrapper stream (mirrors real Kafka schema pattern)
            CREATE TYPE AccountStatus AS ENUM (
                ACTIVE   = 0,
                INACTIVE = 1,
                SUSPENDED = 2
            );
            
            CREATE TYPE CustomerProfile AS STRUCT (
                Name   STRING,
                Email  STRING,
                Age    INT32,
                Status test.AccountStatus
            );
            
            CREATE TYPE CustomerRecord AS STRUCT (
                Key   INT32,
                Value test.CustomerProfile
            );
            
            CREATE STREAM Profiles (
                TYPE CustomerRecord AS test.CustomerRecord
            );
            
            WRITE TO test.Profiles
            TYPE CustomerRecord
            VALUES(
                {Key: 1, Value: {Name: 'Alice', Email: 'alice@example.com', Age: 30, Status: test.AccountStatus::ACTIVE}},
                {Key: 2, Value: {Name: 'Bob',   Email: 'bob@example.com',   Age: 25, Status: test.AccountStatus::ACTIVE}},
                {Key: 3, Value: {Name: 'Charlie', Email: 'charlie@example.com', Age: 35, Status: test.AccountStatus::INACTIVE}},
                {Key: 4, Value: {Name: 'Diana', Email: 'diana@example.com', Age: 28, Status: test.AccountStatus::ACTIVE}}
            );
            """;
        
        // Note: We don't execute setup here. Each test will call engine.executeAll(setupScript, queryScript)
    }
    
    // ========================================================================
    // Basic READ tests
    // ========================================================================
    
    @Test
    void testReadAllCustomers() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(5, results.size(), "Should return all 5 customers");
        assertEquals("Alice", results.get(0).get("Name"));
        assertEquals("Bob", results.get(1).get("Name"));
    }
    
    @Test
    void testReadAllProducts() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Products
            TYPE Product *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(5, results.size(), "Should return all 5 products");
        assertEquals("Widget", results.get(0).get("Name"));
    }
    
    @Test
    void testReadAllOrders() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Orders
            TYPE Order *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(6, results.size(), "Should return all 6 orders");
    }
    
    // ========================================================================
    // Projection tests
    // ========================================================================
    
    @Test
    void testReadCustomerNamesOnly() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer Name;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(5, results.size(), "Should return all 5 customers");
        assertTrue(results.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("Name"))),
            "Each result should have only the Name field");
        assertEquals("Alice", results.get(0).get("Name"));
    }
    
    @Test
    void testReadProductPricesAndNames() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Products
            TYPE Product Name, Price;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(5, results.size(), "Should return all 5 products");
        // Use List comparison to also verify field insertion order matches projection order
        var expectedKeys = java.util.List.of("Name", "Price");
        assertTrue(results.stream().allMatch(r ->
            new java.util.ArrayList<>(r.fields().keySet()).equals(expectedKeys)),
            "Each result should have exactly [Name, Price] in that order");
        assertEquals("Widget", results.get(0).get("Name"));
        assertEquals(100, results.get(0).get("Price"));
        assertEquals("Gadget", results.get(1).get("Name"));
        assertEquals(250, results.get(1).get("Price"));
    }
    
    // ========================================================================
    // WHERE clause filtering tests (TODO: implement WHERE support)
    // ========================================================================
    
    @Test
    void testFilterActiveCustomers() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer *
            WHERE Status = 'ACTIVE';
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(4, results.size(), "Should have 4 active customers");
        assertTrue(results.stream().allMatch(r -> "ACTIVE".equals(r.get("Status"))));
    }
    
    @Test
    void testFilterCustomersByAge() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer *
            WHERE Age >= 30;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(3, results.size(), "Should have 3 customers age >= 30");
    }
    
    @Test
    void testFilterExpensiveProducts() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Products
            TYPE Product *
            WHERE Price > 200;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(2, results.size(), "Should have 2 products over $200");
    }
    
    @Test
    void testFilterProductsByCategory() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Products
            TYPE Product *
            WHERE Category = 'Electronics';
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(2, results.size(), "Should have 2 electronics products");
    }
    
    @Test
    void testFilterInStockProducts() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Products
            TYPE Product *
            WHERE InStock = true;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(4, results.size(), "Should have 4 in-stock products");
    }
    
    @Test
    void testFilterLargeOrders() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Orders
            TYPE Order *
            WHERE Total >= 200;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(4, results.size(), "Should have 4 orders >= $200");
    }
    
    // ========================================================================
    // Combined projection + filtering tests
    // ========================================================================
    
    @Test
    void testProjectionWithFilter() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer Name, Age
            WHERE Status = 'ACTIVE';
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(4, results.size(), "Should return 4 active customers");
        assertTrue(results.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("Name", "Age"))),
            "Each result should have only Name and Age fields");
    }
    
    @Test
    void testComplexFilter() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Products
            TYPE Product Name, Price
            WHERE Category = 'Tools' AND Price > 100;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(1, results.size(), "Should return 1 product (Gizmo)");
        assertEquals("Gizmo", results.get(0).get("Name"));
        assertEquals(150, results.get(0).get("Price"));
        assertTrue(results.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("Name", "Price"))),
            "Each result should have only Name and Price fields");
    }
    
    // ========================================================================
    // Edge cases
    // ========================================================================
    
    @Test
    void testReadWithNoMatches() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer *
            WHERE Age > 100;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(0, results.size(), "Should return no customers over 100");
    }
    
    @Test
    void testMultipleReadsInSequence() {
        String script = """
            USE CONTEXT test;
            
            READ FROM test.Customers
            TYPE Customer *;
            
            READ FROM test.Products
            TYPE Product *;
            
            READ FROM test.Orders
            TYPE Order *;
            """;
        
        engine.executeAll(setupScript, script);
        
        // Last query result should be Orders
        var results = engine.getLastQueryResult();
        assertEquals(6, results.size(), "Last READ should return orders");
        assertTrue(results.get(0).fields().containsKey("OrderId"), 
            "Last result should be Order records");
    }
    
    // ========================================================================
    // Multi-type stream tests
    // ========================================================================
    
    @Test
    void testReadSingleTypeFromMultiTypeStream() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(2, results.size(), "Should return 2 UserCreated events");
        assertEquals(1, results.get(0).get("UserId"));
        assertEquals("alice", results.get(0).get("Username"));
        assertEquals(2, results.get(1).get("UserId"));
        assertEquals("bob", results.get(1).get("Username"));
    }
    
    @Test
    void testReadMultipleTypesInSingleQuery() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated *
            TYPE UserUpdated *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        // Should return UserCreated (2) + UserUpdated (2) = 4 events total
        assertEquals(4, results.size(), "Should return 4 events (2 created + 2 updated)");

        var createdRecords = results.stream().filter(r -> r.fields().containsKey("Username")).toList();
        var updatedRecords = results.stream().filter(r -> r.fields().containsKey("Field")).toList();
        assertEquals(2, createdRecords.size(), "Should have 2 UserCreated records");
        assertEquals(2, updatedRecords.size(), "Should have 2 UserUpdated records");

        // Verify UserCreated field values
        assertEquals(1,       createdRecords.get(0).get("UserId"));
        assertEquals("alice", createdRecords.get(0).get("Username"));
        assertEquals(2,       createdRecords.get(1).get("UserId"));
        assertEquals("bob",   createdRecords.get(1).get("Username"));

        // Verify UserUpdated field values
        assertEquals(1,       updatedRecords.get(0).get("UserId"));
        assertEquals("Email", updatedRecords.get(0).get("Field"));
        assertEquals(2,       updatedRecords.get(1).get("UserId"));
        assertEquals("Username", updatedRecords.get(1).get("Field"));
    }
    
    @Test
    void testReadAllThreeTypesInSingleQuery() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated *
            TYPE UserUpdated *
            TYPE UserDeleted *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        // Should return all 5 events
        assertEquals(5, results.size(), "Should return all 5 events from stream");

        var createdRecords = results.stream().filter(r -> r.fields().containsKey("Username")).toList();
        var updatedRecords = results.stream().filter(r -> r.fields().containsKey("Field")).toList();
        var deletedRecords = results.stream().filter(r -> r.fields().containsKey("Reason")).toList();
        assertEquals(2, createdRecords.size(), "Should have 2 UserCreated");
        assertEquals(2, updatedRecords.size(), "Should have 2 UserUpdated");
        assertEquals(1, deletedRecords.size(), "Should have 1 UserDeleted");
        assertEquals(2, deletedRecords.get(0).get("UserId"), "Deleted user should be UserId=2");
        assertEquals("Account closed by user", deletedRecords.get(0).get("Reason"));
    }
    
    @Test
    void testReadSubsetOfTypesWithProjection() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated UserId, Username
            TYPE UserDeleted UserId, Reason;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        // Should return UserCreated (2) + UserDeleted (1) = 3 events
        assertEquals(3, results.size(), "Should return 3 events (2 created + 1 deleted)");
        
        var createdResults = results.stream().filter(r -> r.fields().containsKey("Username")).toList();
        assertEquals(2, createdResults.size(), "Should have 2 UserCreated results");
        assertTrue(createdResults.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("UserId", "Username"))),
            "UserCreated results should have only UserId and Username");
        var deletedResults = results.stream().filter(r -> r.fields().containsKey("Reason")).toList();
        assertEquals(1, deletedResults.size(), "Should have 1 UserDeleted result");
        assertTrue(deletedResults.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("UserId", "Reason"))),
            "UserDeleted results should have only UserId and Reason");
    }
    
    @Test
    void testReadMultipleTypesWithDifferentFilters() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated * WHERE UserId = 1
            TYPE UserUpdated * WHERE Field = 'Email';
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(2, results.size(), "Should return 2 filtered events");
    }
    
    @Test
    void testReadMultipleTypesWithMixedProjectionAndFilters() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated UserId, Username WHERE UserId > 1
            TYPE UserDeleted *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(2, results.size(), "Should return 2 events (1 created + 1 deleted)");
        var createdResult = results.stream().filter(r -> r.fields().containsKey("Username")).findFirst();
        assertTrue(createdResult.isPresent(), "Should have a UserCreated result");
        assertEquals(2, createdResult.get().get("UserId"), "Should be bob (UserId=2)");
        assertEquals("bob", createdResult.get().get("Username"));
        assertTrue(createdResult.get().fields().keySet().equals(java.util.Set.of("UserId", "Username")),
            "UserCreated should have only UserId and Username");
    }
    
    @Test
    void testReadTwoTypesOmittingThird() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated *
            TYPE UserDeleted *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        // Should return UserCreated (2) + UserDeleted (1) = 3 events
        // UserUpdated events should be excluded
        assertEquals(3, results.size(), "Should return 3 events, excluding UserUpdated");
        
        // Verify UserUpdated is not in results
        var allRecords = engine.getStream(EVENTS_STREAM);
        long updatedInStream = allRecords.stream()
            .filter(r -> "UserUpdated".equals(r.typeName()))
            .count();
        
        assertEquals(2, updatedInStream, "Stream should still have 2 UserUpdated events");

        // Verify the returned results are the right types
        assertTrue(results.stream().anyMatch(r -> r.fields().containsKey("Username")),
            "UserCreated records should be present");
        assertTrue(results.stream().anyMatch(r -> r.fields().containsKey("Reason")),
            "UserDeleted record should be present");
        assertTrue(results.stream().noneMatch(r -> r.fields().containsKey("Field")),
            "UserUpdated records must not appear in results");
    }
    
    @Test
    void testReadSpecificTypeFromMultiTypeStream() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserUpdated *;
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(2, results.size(), "Should return 2 UserUpdated events");
        assertEquals(1, results.get(0).get("UserId"));
        assertEquals("Email", results.get(0).get("Field"));
        assertEquals(2, results.get(1).get("UserId"));
        assertEquals("Username", results.get(1).get("Field"));
    }
    
    @Test
    void testMultipleTypesWrittenInterleaved() {
        String script = """
            USE CONTEXT test;
            
            -- Add more events in different order
            WRITE TO test.Events
            TYPE UserCreated
            VALUES({UserId: 3, Username: 'charlie', Email: 'charlie@example.com'});
            
            WRITE TO test.Events
            TYPE UserDeleted
            VALUES({UserId: 1, Reason: 'Duplicate account'});
            
            WRITE TO test.Events
            TYPE UserUpdated
            VALUES({UserId: 3, Field: 'Email', OldValue: 'charlie@example.com', NewValue: 'chuck@example.com'});
            
            -- Now read multiple types
            READ FROM test.Events
            TYPE UserCreated *
            TYPE UserDeleted *;
            """;
        
        engine.executeAll(setupScript, script);
        var results = engine.getLastQueryResult();
        
        // Should have 3 UserCreated (original 2 + charlie) + 2 UserDeleted (original 1 + new) = 5 events
        assertEquals(5, results.size(), "Should have 5 events total (3 created + 2 deleted)");

        var createdRecords = results.stream().filter(r -> r.fields().containsKey("Username")).toList();
        var deletedRecords = results.stream().filter(r -> r.fields().containsKey("Reason")).toList();
        assertEquals(3, createdRecords.size(), "Should have 3 UserCreated after extra write");
        assertEquals(2, deletedRecords.size(), "Should have 2 UserDeleted after extra write");

        // Verify the new charlie record is present
        assertTrue(createdRecords.stream().anyMatch(r -> "charlie".equals(r.get("Username"))),
            "charlie should be present after interleaved write");
        // Verify both deletions are present
        var deletedUserIds = deletedRecords.stream().map(r -> (Integer) r.get("UserId")).sorted().toList();
        assertEquals(java.util.List.of(1, 2), deletedUserIds,
            "Both UserId=1 and UserId=2 should be deleted");
    }
    
    @Test
    void testFilterOnMultiTypeStream() {
        String query = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserUpdated *
            WHERE Field = 'Email';
            """;
        
        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();
        
        assertEquals(1, results.size(), "Should have 1 email update event");
        assertEquals("Email", results.get(0).get("Field"));
    }
    
    @Test
    void testVerifyStreamContainsAllTypes() {
        // Execute setup to populate data
        engine.execute(setupScript);
        
        // Read the raw stream to verify it contains all types
        var allRecords = engine.getStream(EVENTS_STREAM);
        
        assertEquals(5, allRecords.size(), "Stream should contain 5 total events");
        
        // Count by type
        long createdCount = allRecords.stream()
            .filter(r -> "UserCreated".equals(r.typeName()))
            .count();
        long updatedCount = allRecords.stream()
            .filter(r -> "UserUpdated".equals(r.typeName()))
            .count();
        long deletedCount = allRecords.stream()
            .filter(r -> "UserDeleted".equals(r.typeName()))
            .count();
        
        assertEquals(2, createdCount, "Should have 2 UserCreated events");
        assertEquals(2, updatedCount, "Should have 2 UserUpdated events");
        assertEquals(1, deletedCount, "Should have 1 UserDeleted event");
        
        System.out.println("Events stream composition:");
        System.out.println("  UserCreated: " + createdCount);
        System.out.println("  UserUpdated: " + updatedCount);
        System.out.println("  UserDeleted: " + deletedCount);
    }
    
    @Test
    void testSequentialReadsOfDifferentTypes() {
        String script = """
            USE CONTEXT test;
            
            READ FROM test.Events
            TYPE UserCreated *;
            
            READ FROM test.Events
            TYPE UserUpdated *;
            
            READ FROM test.Events
            TYPE UserDeleted *;
            """;
        
        engine.executeAll(setupScript, script);
        
        // Last query result should be UserDeleted
        var results = engine.getLastQueryResult();
        assertEquals(1, results.size(), "Last READ should return 1 UserDeleted event");
        assertTrue(results.get(0).fields().containsKey("Reason"), 
            "Last result should have Reason field (UserDeleted)");
    }

    // ========================================================================
    // Alias projection tests — output field name is controlled by AS clause
    // ========================================================================

    @Test
    void testSimpleAlias() {
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Key AS CustomerId;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(4, results.size(), "Should return all 4 profiles");
        assertTrue(results.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("CustomerId"))),
            "Output field must be 'CustomerId', not 'Key'");
        assertFalse(results.get(0).fields().containsKey("Key"),
            "Original field name 'Key' must not appear in output");
        assertEquals(1, results.get(0).get("CustomerId"));
        assertEquals(2, results.get(1).get("CustomerId"));
        assertEquals(3, results.get(2).get("CustomerId"));
        assertEquals(4, results.get(3).get("CustomerId"));
    }

    @Test
    void testMultipleAliases() {
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Key AS Id, Value.Name AS FullName, Value.Age AS Years;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(4, results.size());
        // All three output fields must appear under alias names
        assertTrue(results.stream().allMatch(r ->
            r.fields().containsKey("Id") && r.fields().containsKey("FullName") && r.fields().containsKey("Years")
        ), "All alias names must be present");
        // None of the original names should appear
        assertTrue(results.stream().noneMatch(r ->
            r.fields().containsKey("Key") || r.fields().containsKey("Name") || r.fields().containsKey("Age")
        ), "Original field names must not appear in output");
        // Spot-check values
        assertEquals(1,       results.get(0).get("Id"));
        assertEquals("Alice", results.get(0).get("FullName"));
        assertEquals(30,      results.get(0).get("Years"));
    }

    // ========================================================================
    // Nested field projection tests — Value.Field navigates into nested struct
    // ========================================================================

    @Test
    void testNestedFieldProjection() {
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Value.Name AS CustomerName;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(4, results.size(), "Should return all 4 profiles");
        assertTrue(results.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("CustomerName"))),
            "Output must have only 'CustomerName'");
        assertEquals("Alice",   results.get(0).get("CustomerName"));
        assertEquals("Bob",     results.get(1).get("CustomerName"));
        assertEquals("Charlie", results.get(2).get("CustomerName"));
        assertEquals("Diana",   results.get(3).get("CustomerName"));
    }

    @Test
    void testKeyAndNestedFieldProjection() {
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Key AS CustomerId, Value.Name AS CustomerName;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(4, results.size());
        assertTrue(results.stream().allMatch(r ->
            r.fields().keySet().equals(java.util.Set.of("CustomerId", "CustomerName"))
        ), "Output must have exactly {CustomerId, CustomerName}");
        assertEquals(1,       results.get(0).get("CustomerId"));
        assertEquals("Alice", results.get(0).get("CustomerName"));
        assertEquals(2,       results.get(1).get("CustomerId"));
        assertEquals("Bob",   results.get(1).get("CustomerName"));
        assertEquals(4,       results.get(3).get("CustomerId"));
        assertEquals("Diana", results.get(3).get("CustomerName"));
    }

    @Test
    void testProjectionOutputFieldOrder() {
        // Projection order in the query: Name first, then Id
        // Schema order is: Key first, then Value — output must follow the query, not the schema
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Value.Name AS Name, Key AS Id;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(4, results.size());
        var keys = new java.util.ArrayList<>(results.get(0).fields().keySet());
        assertEquals(java.util.List.of("Name", "Id"), keys,
            "Output field order must match projection list order, not schema order");
        assertEquals("Alice", results.get(0).get("Name"));
        assertEquals(1,       results.get(0).get("Id"));
    }

    // ========================================================================
    // Nested WHERE tests — WHERE on nested struct fields
    // ========================================================================

    @Test
    void testWhereOnNestedField() {
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord *
            WHERE Value.Status = test.AccountStatus::ACTIVE;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(3, results.size(), "Should return 3 active profiles (Alice, Bob, Diana)");
        // All returned records must have Key 1, 2, or 4 (not 3 = Charlie who is INACTIVE)
        var keys = results.stream().map(r -> (Integer) r.get("Key")).sorted().toList();
        assertEquals(java.util.List.of(1, 2, 4), keys,
            "Only Alice(1), Bob(2), and Diana(4) should be returned");
    }

    @Test
    void testWhereOnNestedFieldWithInactiveFilter() {
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord *
            WHERE Value.Status = test.AccountStatus::INACTIVE;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(1, results.size(), "Only Charlie is INACTIVE");
        assertEquals(3, results.get(0).get("Key"), "Charlie has Key=3");
    }

    @Test
    void testNestedProjectionWithNestedWhere() {
        // This test covers the bug where WHERE on Value.Status used to zero out results
        // because collectExprFields incorrectly added leaf "Status" instead of root "Value"
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Key AS CustomerId, Value.Name AS CustomerName
            WHERE Value.Status = test.AccountStatus::ACTIVE;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(3, results.size(), "Should return 3 active profiles");
        assertTrue(results.stream().allMatch(r ->
            r.fields().keySet().equals(java.util.Set.of("CustomerId", "CustomerName"))
        ), "Output must have exactly {CustomerId, CustomerName}");
        // WHERE field (Value.Status) must not appear in the projected output
        assertTrue(results.stream().noneMatch(r ->
            r.fields().containsKey("Value") || r.fields().containsKey("Status")
        ), "WHERE-only fields must not leak into projection output");
        assertEquals(1,       results.get(0).get("CustomerId"));
        assertEquals("Alice", results.get(0).get("CustomerName"));
        assertEquals(2,       results.get(1).get("CustomerId"));
        assertEquals("Bob",   results.get(1).get("CustomerName"));
        assertEquals(4,       results.get(2).get("CustomerId"));
        assertEquals("Diana", results.get(2).get("CustomerName"));
    }

    @Test
    void testWhereFieldDoesNotLeakIntoProjection() {
        // Project only Key (as CustomerId) but filter by Value.Status
        // readFields will be {"Key","Value"} internally, but output must only have "CustomerId"
        String query = """
            USE CONTEXT test;

            READ FROM test.Profiles
            TYPE CustomerRecord Key AS CustomerId
            WHERE Value.Status = test.AccountStatus::ACTIVE;
            """;

        engine.executeAll(setupScript, query);
        var results = engine.getLastQueryResult();

        assertEquals(3, results.size(), "Should return 3 active profiles");
        assertTrue(results.stream().allMatch(r -> r.fields().keySet().equals(java.util.Set.of("CustomerId"))),
            "Output must have ONLY 'CustomerId' — 'Value', 'Key', 'Status' must not appear");
        var ids = results.stream().map(r -> (Integer) r.get("CustomerId")).sorted().toList();
        assertEquals(java.util.List.of(1, 2, 4), ids);
    }
}