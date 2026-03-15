package kafkasql.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kafkasql.lang.input.StringInput;
import kafkasql.pipeline.phases.LintPhase;
import kafkasql.pipeline.phases.ParsePhase;
import kafkasql.pipeline.phases.SemanticPhase;

@DisplayName("Pipeline execution")
class PipelineTest {
    
    @Test
    @DisplayName("Should execute all phases successfully")
    void shouldExecuteAllPhases() {
        var pipeline = Pipeline.builder()
            .addPhase(new ParsePhase())
            .addPhase(new SemanticPhase())
            .addPhase(new LintPhase())
            .build();
        
        var context = PipelineContext.builder()
            .inputs(List.of(new StringInput("test", """
                CREATE TYPE Status AS ENUM (ACTIVE = 1, INACTIVE = 2);
                """)))
            .workingDir(Paths.get("."))
            .build();
        
        var result = pipeline.execute(context);
        
        assertTrue(result.completed(), "Pipeline should complete");
        assertTrue(result.succeeded(), "Pipeline should succeed without errors");
        assertNotNull(result.model().parseResult(), "Should have parse result");
        assertNotNull(result.model().semanticModel(), "Should have semantic model");
    }
    
    @Test
    @DisplayName("Should stop on parse error")
    void shouldStopOnParseError() {
        var pipeline = Pipeline.builder()
            .addPhase(new ParsePhase())
            .addPhase(new SemanticPhase())
            .build();
        
        var context = PipelineContext.builder()
            .inputs(List.of(new StringInput("test", """
                CREATE TYPE INVALID SYNTAX
                """)))
            .workingDir(Paths.get("."))
            .build();
        
        var result = pipeline.execute(context);
        
        assertFalse(result.completed(), "Pipeline should stop early");
        assertFalse(result.succeeded(), "Pipeline should have errors");
        assertTrue(result.diagnostics().hasError(), "Should have parse errors");
    }
    
    @Test
    @DisplayName("Should stop on semantic error")
    void shouldStopOnSemanticError() {
        var pipeline = Pipeline.builder()
            .addPhase(new ParsePhase())
            .addPhase(new SemanticPhase())
            .addPhase(new LintPhase())
            .build();
        
        var context = PipelineContext.builder()
            .inputs(List.of(new StringInput("test", """
                CREATE TYPE Test AS STRUCT (
                    Field UnknownType
                );
                """)))
            .workingDir(Paths.get("."))
            .build();
        
        var result = pipeline.execute(context);
        
        assertFalse(result.completed(), "Pipeline should stop early");
        assertFalse(result.succeeded(), "Pipeline should have errors");
        assertTrue(result.diagnostics().hasError(), "Should have semantic errors");
        // LintPhase should not have run
    }
    
    @Test
    @DisplayName("Should continue on lint warnings")
    void shouldContinueOnLintWarnings() {
        var pipeline = Pipeline.builder()
            .addPhase(new ParsePhase())
            .addPhase(new SemanticPhase())
            .addPhase(new LintPhase())
            .build();
        
        var context = PipelineContext.builder()
            .inputs(List.of(new StringInput("test", """
                CREATE TYPE userStatus AS ENUM (Active = 1);
                """)))
            .workingDir(Paths.get("."))
            .build();
        
        var result = pipeline.execute(context);
        
        assertTrue(result.completed(), "Pipeline should complete");
        assertTrue(result.diagnostics().hasWarning(), "Should have lint warnings");
        assertFalse(result.diagnostics().hasError(), "Should not have errors");
    }
}
