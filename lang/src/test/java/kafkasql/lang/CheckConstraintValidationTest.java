package kafkasql.lang;

import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.SemanticModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHECK constraint validation during literal binding
 */
class CheckConstraintValidationTest {

    @Test
    void validValueShouldPass() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE posint AS SCALAR INT32 CHECK(value >= 0);
            
            CREATE STREAM events (
                TYPE val AS STRUCT (
                    name STRING,
                    val test.posint
                )
            );
            
            WRITE TO test.events TYPE val VALUES (@{ name: 'thing', val: 99 });
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(), 
            "Valid positive value should pass CHECK constraint: " + model.diags().all());
    }

    @Test
    void invalidValueShouldFail() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE posint AS SCALAR INT32 CHECK(value >= 0);
            
            CREATE STREAM events (
                TYPE val AS STRUCT (
                    name STRING,
                    val test.posint
                )
            );
            
            WRITE TO test.events TYPE val VALUES (@{ name: 'thing', val: -99 });
            """;

        var model = compile(source);
        assertTrue(model.diags().hasError(), 
            "Negative value should fail CHECK constraint");
        
        String errorMsg = model.diags().all().toString();
        assertTrue(errorMsg.contains("CHECK constraint failed") || 
                   errorMsg.contains("INVALID_CHECK_CONSTRAINT"),
            "Error should mention CHECK constraint failure: " + errorMsg);
    }

    @Test
    void boundaryValueShouldPass() {
        String source = """
            CREATE CONTEXT test;
            USE CONTEXT test;
            
            CREATE TYPE posint AS SCALAR INT32 CHECK(value >= 0);
            
            CREATE STREAM events (
                TYPE val AS STRUCT (
                    name STRING,
                    val test.posint
                )
            );
            
            WRITE TO test.events TYPE val VALUES (@{ name: 'thing', val: 0 });
            """;

        var model = compile(source);
        assertFalse(model.diags().hasError(), 
            "Boundary value (0) should pass CHECK constraint: " + model.diags().all());
    }

    private SemanticModel compile(String source) {
        Input input = new StringInput("CheckConstraintValidationTest.kafka", source);
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(List.of(input), args);
        return KafkaSqlParser.bind(parseResult);
    }
}
