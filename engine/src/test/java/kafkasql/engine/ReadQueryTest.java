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
                @{Id: 1, Name: 'Alice', Email: 'alice@example.com', Age: 30, Status: 'ACTIVE'},
                @{Id: 2, Name: 'Bob', Email: 'bob@example.com', Age: 25, Status: 'ACTIVE'},
                @{Id: 3, Name: 'Charlie', Email: 'charlie@example.com', Age: 35, Status: 'INACTIVE'},
                @{Id: 4, Name: 'Diana', Email: 'diana@example.com', Age: 28, Status: 'ACTIVE'},
                @{Id: 5, Name: 'Eve', Email: 'eve@example.com', Age: 42, Status: 'ACTIVE'}
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
                @{Id: 101, Name: 'Widget', Category: 'Tools', Price: 100, InStock: true},
                @{Id: 102, Name: 'Gadget', Category: 'Electronics', Price: 250, InStock: true},
                @{Id: 103, Name: 'Gizmo', Category: 'Tools', Price: 150, InStock: false},
                @{Id: 104, Name: 'Doohickey', Category: 'Home', Price: 75, InStock: true},
                @{Id: 105, Name: 'Thingamajig', Category: 'Electronics', Price: 300, InStock: true}
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
                @{OrderId: 1001, CustomerId: 1, ProductId: 101, Quantity: 2, Total: 200},
                @{OrderId: 1002, CustomerId: 2, ProductId: 102, Quantity: 1, Total: 250},
                @{OrderId: 1003, CustomerId: 1, ProductId: 103, Quantity: 1, Total: 150},
                @{OrderId: 1004, CustomerId: 4, ProductId: 104, Quantity: 3, Total: 225},
                @{OrderId: 1005, CustomerId: 2, ProductId: 105, Quantity: 1, Total: 300},
                @{OrderId: 1006, CustomerId: 5, ProductId: 101, Quantity: 1, Total: 100}
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
                @{UserId: 1, Username: 'alice', Email: 'alice@example.com'},
                @{UserId: 2, Username: 'bob', Email: 'bob@example.com'}
            );
            
            WRITE TO test.Events
            TYPE UserUpdated
            VALUES(
                @{UserId: 1, Field: 'Email', OldValue: 'alice@example.com', NewValue: 'alice.new@example.com'},
                @{UserId: 2, Field: 'Username', OldValue: 'bob', NewValue: 'robert'}
            );
            
            WRITE TO test.Events
            TYPE UserDeleted
            VALUES(
                @{UserId: 2, Reason: 'Account closed by user'}
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
    // Projection tests (TODO: implement projection support)
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
        
        // TODO: Once projection is implemented, verify only Name field is present
        System.out.println("Customer names projection:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Once projection is implemented, verify only Name and Price are present
        System.out.println("Product name/price projection:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Once WHERE is implemented, should return 4 active customers
        System.out.println("Active customers (WHERE Status = 'ACTIVE'):");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(4, results.size(), "Should have 4 active customers");
        // assertTrue(results.stream().allMatch(r -> "ACTIVE".equals(r.get("Status"))));
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
        
        // TODO: Should return 3 customers (Alice:30, Charlie:35, Eve:42)
        System.out.println("Customers age >= 30:");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(3, results.size(), "Should have 3 customers age >= 30");
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
        
        // TODO: Should return 2 products (Gadget:250, Thingamajig:300)
        System.out.println("Products with Price > 200:");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(2, results.size(), "Should have 2 products over $200");
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
        
        // TODO: Should return 2 products (Gadget, Thingamajig)
        System.out.println("Electronics products:");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(2, results.size(), "Should have 2 electronics products");
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
        
        // TODO: Should return 4 products (all except Gizmo)
        System.out.println("In-stock products:");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(4, results.size(), "Should have 4 in-stock products");
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
        
        // TODO: Should return 4 orders with Total >= 200
        System.out.println("Orders with Total >= 200:");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(4, results.size(), "Should have 4 orders >= $200");
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
        
        // TODO: Should return 4 customers with only Name and Age fields
        System.out.println("Active customer names and ages:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Should return 1 product (Gizmo - Tools category, Price 150)
        System.out.println("Tools over $100:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Should return empty list when filtering works
        System.out.println("Customers over 100 years old (should be none):");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // Results should contain both types
        // Note: Order depends on how they were written to the stream
        System.out.println("Multiple type query results:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        System.out.println("All types query results:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Once projection works, verify only specified fields are present
        System.out.println("Multi-type with projection:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Once filtering works:
        // - Should return 1 UserCreated (UserId=1) + 1 UserUpdated (Field='Email') = 2 events
        System.out.println("Multi-type with different filters:");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(2, results.size(), "Should return 2 filtered events");
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
        
        // TODO: Once projection and filtering work:
        // - UserCreated with UserId > 1 (bob) with only UserId, Username
        // - All UserDeleted events with all fields
        System.out.println("Mixed projection and filters:");
        results.forEach(r -> System.out.println("  " + r));
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
        System.out.println("Results excluding UserUpdated:");
        results.forEach(r -> System.out.println("  " + r));
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
            VALUES(@{UserId: 3, Username: 'charlie', Email: 'charlie@example.com'});
            
            WRITE TO test.Events
            TYPE UserDeleted
            VALUES(@{UserId: 1, Reason: 'Duplicate account'});
            
            WRITE TO test.Events
            TYPE UserUpdated
            VALUES(@{UserId: 3, Field: 'Email', OldValue: 'charlie@example.com', NewValue: 'chuck@example.com'});
            
            -- Now read multiple types
            READ FROM test.Events
            TYPE UserCreated *
            TYPE UserDeleted *;
            """;
        
        engine.executeAll(setupScript, script);
        var results = engine.getLastQueryResult();
        
        // Should have 3 UserCreated (original 2 + charlie) + 2 UserDeleted (original 1 + new) = 5 events
        assertEquals(5, results.size(), "Should have 5 events total (3 created + 2 deleted)");
        
        System.out.println("After interleaved writes, reading created + deleted:");
        results.forEach(r -> System.out.println("  " + r));
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
        
        // TODO: Once filtering works, should return 1 UserUpdated event (Email field change)
        System.out.println("UserUpdated events where Field = 'Email':");
        results.forEach(r -> System.out.println("  " + r));
        
        // When filtering works:
        // assertEquals(1, results.size(), "Should have 1 email update event");
        // assertEquals("Email", results.get(0).get("Field"));
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
}