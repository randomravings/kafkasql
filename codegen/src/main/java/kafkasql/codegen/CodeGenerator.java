package kafkasql.codegen;

import kafkasql.lang.IncludeResolver;
import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.FileInput;
import kafkasql.lang.input.Input;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.runtime.diagnostics.Diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Standalone tool to generate Java code from KafkaSQL schemas.
 */
public class CodeGenerator {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: CodeGenerator <kafkasql-file> <output-dir>");
            System.err.println("Example: CodeGenerator examples/sys/persistence/events.kafka persistence/src/main/java");
            System.exit(1);
        }
        
        Path schemaFile = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        
        System.out.println("Parsing: " + schemaFile);
        
        // Parse and bind the schema
        // Working directory is the current directory (where gradle runs from via workingDir)
        Path workingDir = Path.of(".").toAbsolutePath().normalize();
        Input input = FileInput.of(schemaFile);
        
        // Resolve includes first
        Diagnostics includeDiags = new Diagnostics();
        List<Input> inputs = IncludeResolver.buildIncludeOrder(
            List.of(input),
            workingDir,
            includeDiags
        );
        
        if (includeDiags.hasError()) {
            System.err.println("Include resolution errors:");
            includeDiags.errors().forEach(System.err::println);
            System.exit(1);
        }
        
        // Parse all resolved files
        KafkaSqlArgs parseArgs = new KafkaSqlArgs(workingDir, false, false);  // Includes already resolved
        ParseResult parseResult = KafkaSqlParser.parse(inputs, parseArgs);
        
        if (parseResult.diags().hasError()) {
            System.err.println("Parse errors:");
            parseResult.diags().errors().forEach(System.err::println);
            System.exit(1);
        }
        
        SemanticModel model = KafkaSqlParser.bind(parseResult);
        
        if (model.diags().hasError()) {
            System.err.println("Semantic errors:");
            model.diags().errors().forEach(System.err::println);
            System.exit(1);
        }
        
        System.out.println("Generating Java code...");
        
        // Generate Java code
        Compiler compiler = new Compiler(model);
        Map<String, String> generated = compiler.compile();
        
        if (generated.isEmpty()) {
            System.out.println("No types found to generate.");
            return;
        }
        
        System.out.println("Generated " + generated.size() + " files:");
        
        // Write generated files
        for (Map.Entry<String, String> entry : generated.entrySet()) {
            String className = entry.getKey();
            String sourceCode = entry.getValue();
            
            // Convert class name to file path (e.g., "sys/persistence/EventType" -> "sys/persistence/EventType.java")
            Path outputPath = outputDir.resolve(className + ".java");
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, sourceCode);
            
            System.out.println("  " + outputPath);
        }
        
        System.out.println("Done!");
    }
}
