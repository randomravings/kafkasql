package kafkasql.lang;

import kafkasql.lang.semantic.SemanticModel;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.runtime.Name;
import kafkasql.runtime.type.CheckConstraint;
import kafkasql.runtime.type.ScalarType;
import kafkasql.runtime.type.StructType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckConstraintsTest {

    @Test
    void scalarWithSingleCheck() {
        String source = """
            CREATE TYPE Money AS SCALAR INT32
            CHECK (value > 0);
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(), "Should have no errors");

        ScalarType money = (ScalarType) model.bindings().get(
            model.symbols().lookupType(Name.of("Money")).orElseThrow()
        );

        assertTrue(money.check().isPresent(), "Should have a check constraint");
        CheckConstraint check = money.check().get();
        assertEquals("value", check.name(), "Scalar check should reference 'value'");
        assertEquals(1, check.referencedFields().size());
        assertTrue(check.referencedFields().contains("value"));
    }

    @Test
    void scalarWithMultipleChecksShouldFail() {
        String source = """
            CREATE TYPE Money AS SCALAR INT32
            CHECK (value > 0)
            CHECK (value < 1000000);
            """;

        var model = compile(source);
        assertTrue(model.diags().hasError(), "Should have errors for multiple checks");
    }

    @Test
    void scalarWithoutCheck() {
        String source = """
            CREATE TYPE Money AS SCALAR INT32;
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(), "Should have no errors");

        ScalarType money = (ScalarType) model.bindings().get(
            model.symbols().lookupType(Name.of("Money")).orElseThrow()
        );

        assertFalse(money.check().isPresent(), "Should have no check constraint");
    }

    @Test
    void structWithNamedConstraints() {
        String source = """
            CREATE TYPE DateRange AS STRUCT (
              startDate INT32,
              endDate INT32
            )
            CONSTRAINT valid_range (CHECK (startDate < endDate));
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(), "Should have no errors");

        StructType dateRange = (StructType) model.bindings().get(
            model.symbols().lookupType(Name.of("DateRange")).orElseThrow()
        );

        assertEquals(1, dateRange.constraints().size(), "Should have one constraint");
        CheckConstraint constraint = dateRange.constraints().get(0);
        assertEquals("valid_range", constraint.name());
        assertEquals(2, constraint.referencedFields().size());
        assertTrue(constraint.referencedFields().contains("startDate"));
        assertTrue(constraint.referencedFields().contains("endDate"));
    }

    @Test
    void structWithMultipleNamedConstraints() {
        String source = """
            CREATE TYPE Person AS STRUCT (
              name STRING,
              age INT32,
              email STRING NULL
            )
            CONSTRAINT positive_age (CHECK (age > 0))
            CONSTRAINT has_contact (CHECK (email IS NOT NULL OR name <> ''));
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(), "Should have no errors");

        StructType person = (StructType) model.bindings().get(
            model.symbols().lookupType(Name.of("Person")).orElseThrow()
        );

        assertEquals(2, person.constraints().size(), "Should have two constraints");
        
        CheckConstraint ageCheck = person.constraints().stream()
            .filter(c -> c.name().equals("positive_age"))
            .findFirst()
            .orElseThrow();
        assertTrue(ageCheck.referencedFields().contains("age"));

        CheckConstraint contactCheck = person.constraints().stream()
            .filter(c -> c.name().equals("has_contact"))
            .findFirst()
            .orElseThrow();
        assertTrue(contactCheck.referencedFields().contains("email"));
        assertTrue(contactCheck.referencedFields().contains("name"));
    }

    @Test
    void structWithDirectCheckShouldFail() {
        String source = """
            CREATE TYPE DateRange AS STRUCT (
              startDate INT32,
              endDate INT32
            )
            CHECK (startDate < endDate);
            """;

        var model = compile(source);
        assertFalse(model.diags().all().isEmpty(), "Should have errors for direct CHECK on struct");
        assertTrue(model.diags().hasError());
    }

    @Test
    void structWithDuplicateConstraintNamesShouldFail() {
        String source = """
            CREATE TYPE Person AS STRUCT (
              age INT32,
              weight INT32
            )
            CONSTRAINT positive (CHECK (age > 0))
            CONSTRAINT positive (CHECK (weight > 0));
            """;

        var model = compile(source);
        assertFalse(model.diags().all().isEmpty(), "Should have errors for duplicate constraint names");
        assertTrue(model.diags().hasError());
    }

    @Test
    void constraintReferencingUnknownFieldShouldFail() {
        String source = """
            CREATE TYPE Person AS STRUCT (
              age INT32
            )
            CONSTRAINT check_height (CHECK (height > 0));
            """;

        var model = compile(source);
        assertFalse(model.diags().all().isEmpty(), "Should have errors for unknown field");
        assertTrue(model.diags().hasError());
    }

    @Test
    void checkMustReturnBoolean() {
        String source = """
            CREATE TYPE Money AS SCALAR INT32
            CHECK (value + 10);
            """;

        var model = compile(source);
        assertFalse(model.diags().all().isEmpty(), "Should have errors for non-boolean check");
        assertTrue(model.diags().hasError());
    }

    private SemanticModel compile(String source) {
        Input input = new StringInput("test.kafka", source);
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(List.of(input), args);
        
        if (parseResult.diags().hasError()) {
            return KafkaSqlParser.bind(parseResult);
        }
        
        return KafkaSqlParser.bind(parseResult);
    }
}
