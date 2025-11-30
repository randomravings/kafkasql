package kafkasql.codegen;

import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.SemanticModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    
    @Test
    void testScalarCodeGen() {
        String script = """
            CREATE SCALAR PersonId AS STRING;
            """;
        
        var model = compile(script);
        var compiler = new Compiler(model);
        Map<String, String> generated = compiler.compile();
        
        assertEquals(1, generated.size());
        assertTrue(generated.containsKey("PersonId"));
        
        String code = generated.get("PersonId");
        assertTrue(code.contains("public record PersonId"));
        assertTrue(code.contains("String value"));
        System.out.println("Generated scalar:");
        System.out.println(code);
    }
    
    @Test
    void testEnumCodeGen() {
        String script = """
            CREATE ENUM Status (
                PENDING: 0,
                ACTIVE: 1,
                COMPLETED: 2
            );
            """;
        
        var model = compile(script);
        var compiler = new Compiler(model);
        Map<String, String> generated = compiler.compile();
        
        assertEquals(1, generated.size());
        assertTrue(generated.containsKey("Status"));
        
        String code = generated.get("Status");
        assertTrue(code.contains("public enum Status"));
        assertTrue(code.contains("PENDING(0)"));
        assertTrue(code.contains("ACTIVE(1)"));
        assertTrue(code.contains("COMPLETED(2)"));
        System.out.println("Generated enum:");
        System.out.println(code);
    }
    
    @Test
    void testStructCodeGen() {
        String script = """
            CREATE STRUCT Person (
                id STRING,
                name STRING,
                age INT32
            );
            """;
        
        var model = compile(script);
        var compiler = new Compiler(model);
        Map<String, String> generated = compiler.compile();
        
        assertEquals(1, generated.size());
        assertTrue(generated.containsKey("Person"));
        
        String code = generated.get("Person");
        assertTrue(code.contains("public record Person"));
        assertTrue(code.contains("String id"));
        assertTrue(code.contains("String name"));
        assertTrue(code.contains("int age"));
        System.out.println("Generated struct:");
        System.out.println(code);
    }
    
    @Test
    void testComplexStructCodeGen() {
        String script = """
            CREATE ENUM Status (
                PENDING: 0,
                ACTIVE: 1
            );
            
            CREATE STRUCT Address (
                street STRING,
                city STRING,
                zipCode STRING NULL
            );
            
            CREATE STRUCT Person (
                id STRING,
                name STRING,
                age INT32,
                status Status,
                address Address,
                tags LIST<STRING>
            );
            """;
        
        var model = compile(script);
        var compiler = new Compiler(model);
        Map<String, String> generated = compiler.compile();
        
        assertEquals(3, generated.size());
        assertTrue(generated.containsKey("Status"));
        assertTrue(generated.containsKey("Address"));
        assertTrue(generated.containsKey("Person"));
        
        String personCode = generated.get("Person");
        assertTrue(personCode.contains("Status status"));
        assertTrue(personCode.contains("Address address"));
        assertTrue(personCode.contains("List<String> tags"));
        
        System.out.println("Generated Status:");
        System.out.println(generated.get("Status"));
        System.out.println("\nGenerated Address:");
        System.out.println(generated.get("Address"));
        System.out.println("\nGenerated Person:");
        System.out.println(personCode);
    }
    
    @Test
    void testPackagedTypes() {
        String script = """
            CREATE CONTEXT example;
            USE CONTEXT example;
            
            CREATE STRUCT Person (
                id STRING,
                name STRING
            );
            """;
        
        var model = compile(script);
        var compiler = new Compiler(model);
        Map<String, String> generated = compiler.compile();
        
        assertEquals(1, generated.size());
        assertTrue(generated.containsKey("example/Person"));
        
        String code = generated.get("example/Person");
        assertTrue(code.contains("package example;"));
        assertTrue(code.contains("public record Person"));
        System.out.println("Generated packaged struct:");
        System.out.println(code);
    }
    
    private SemanticModel compile(String script) {
        Input input = new StringInput("test.kafka", script);
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(List.of(input), args);
        
        if (parseResult.diags().hasError()) {
            throw new RuntimeException("Parse error: " + parseResult.diags().errors());
        }
        
        return KafkaSqlParser.bind(parseResult);
    }
}
