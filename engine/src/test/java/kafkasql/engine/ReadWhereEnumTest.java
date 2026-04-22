package kafkasql.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kafkasql.engine.impl.TestEngine;

/**
 * Verifies WHERE clause filtering when the predicate involves an enum field
 * nested inside a struct — exactly mirroring the customers.Customers use-case:
 *
 *   CustomerRecord { Key: INT32, Value: Customer { Id: INT32, Name: STRING, Status: CustomerStatus } }
 *   WHERE Value.Status = customers.CustomerStatus::ACTIVE
 */
class ReadWhereEnumTest {

    private TestEngine engine;

    /** Full schema + data in a single script so INCLUDE is not needed. */
    private static final String SETUP = """
            CREATE CONTEXT customers;
            USE CONTEXT customers;

            CREATE TYPE CustomerStatus AS ENUM (
                ACTIVE   = 0,
                INACTIVE = 1
            )
            DEFAULT CustomerStatus::ACTIVE;

            CREATE TYPE Customer AS STRUCT (
                Id     INT32,
                Name   STRING,
                Status customers.CustomerStatus
            );

            CREATE STREAM Customers (
                TYPE CustomerRecord AS STRUCT (
                    Key   INT32,
                    Value customers.Customer
                )
            );

            WRITE TO customers.Customers
              TYPE CustomerRecord
              VALUES
                ({ Key: 1, Value: { Id: 1, Name: 'Alice',   Status: customers.CustomerStatus::ACTIVE   } },
                 { Key: 2, Value: { Id: 2, Name: 'Bob',     Status: customers.CustomerStatus::INACTIVE } },
                 { Key: 3, Value: { Id: 3, Name: 'Charlie', Status: customers.CustomerStatus::ACTIVE   } });
            """;

    @BeforeEach
    void setUp() {
        engine = new TestEngine();
    }

    // ── Basic read (no filter) ────────────────────────────────────────────────

    @Test
    void readAll_returnsAllThreeRecords() {
        String query = """
                USE CONTEXT customers;
                READ FROM customers.Customers
                  TYPE CustomerRecord *;
                """;

        engine.executeAll(SETUP, query);
        var results = engine.getLastQueryResult();

        assertEquals(3, results.size(), "Should return all 3 CustomerRecord entries");
    }

    // ── WHERE on nested enum field ────────────────────────────────────────────

    @Test
    void whereEnumEquals_returnsOnlyActiveCustomers() {
        String query = """
                USE CONTEXT customers;
                READ FROM customers.Customers
                  TYPE CustomerRecord *
                  WHERE Value.Status = customers.CustomerStatus::ACTIVE;
                """;

        engine.executeAll(SETUP, query);
        var results = engine.getLastQueryResult();

        assertEquals(2, results.size(), "Should return 2 ACTIVE customers (Alice + Charlie)");

        // Verify the names via the nested Value field
        var names = results.stream()
                .map(sv -> {
                    Object valueField = sv.get("Value");
                    if (valueField instanceof kafkasql.runtime.value.StructValue nested) {
                        return (String) nested.get("Name");
                    }
                    return null;
                })
                .toList();
        assertTrue(names.contains("Alice"),   "Alice (ACTIVE) should be included");
        assertTrue(names.contains("Charlie"), "Charlie (ACTIVE) should be included");
        assertFalse(names.contains("Bob"),    "Bob (INACTIVE) should be excluded");
    }

    @Test
    void whereEnumEquals_inactive_returnsOnlyBob() {
        String query = """
                USE CONTEXT customers;
                READ FROM customers.Customers
                  TYPE CustomerRecord *
                  WHERE Value.Status = customers.CustomerStatus::INACTIVE;
                """;

        engine.executeAll(SETUP, query);
        var results = engine.getLastQueryResult();

        assertEquals(1, results.size(), "Should return 1 INACTIVE customer (Bob)");
        Object valueField = results.get(0).get("Value");
        assertInstanceOf(kafkasql.runtime.value.StructValue.class, valueField);
        assertEquals("Bob", ((kafkasql.runtime.value.StructValue) valueField).get("Name"));
    }

    // ── WHERE on top-level Key (non-enum) field ────────────────────────────────

    @Test
    void whereKeyEquals_returnsSingleRecord() {
        String query = """
                USE CONTEXT customers;
                READ FROM customers.Customers
                  TYPE CustomerRecord *
                  WHERE Key = 2;
                """;

        engine.executeAll(SETUP, query);
        var results = engine.getLastQueryResult();

        assertEquals(1, results.size(), "Should return only Key=2 (Bob)");
        assertEquals(2, results.get(0).get("Key"));
    }

    // ── WHERE combining enum and scalar ──────────────────────────────────────

    @Test
    void whereEnumAndKey_returnsSpecificRecord() {
        String query = """
                USE CONTEXT customers;
                READ FROM customers.Customers
                  TYPE CustomerRecord *
                  WHERE Value.Status = customers.CustomerStatus::ACTIVE AND Key > 1;
                """;

        engine.executeAll(SETUP, query);
        var results = engine.getLastQueryResult();

        // Alice is ACTIVE but Key=1, Charlie is ACTIVE and Key=3
        assertEquals(1, results.size(), "Should return only Charlie (ACTIVE, Key=3)");
        Object valueField = results.get(0).get("Value");
        assertInstanceOf(kafkasql.runtime.value.StructValue.class, valueField);
        assertEquals("Charlie", ((kafkasql.runtime.value.StructValue) valueField).get("Name"));
    }
}
