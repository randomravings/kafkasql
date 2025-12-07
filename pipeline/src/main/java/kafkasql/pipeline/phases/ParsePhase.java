package kafkasql.pipeline.phases;

import kafkasql.lang.IncludeResolver;
import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.Input;
import kafkasql.pipeline.Phase;
import kafkasql.pipeline.PhaseResult;
import kafkasql.pipeline.PipelineContext;
import kafkasql.pipeline.PipelineModel;
import kafkasql.runtime.diagnostics.Diagnostics;

import java.util.List;

/**
 * Parse phase: Lexing and parsing source files into AST.
 * 
 * <p>Produces {@link ParseResult} containing scripts and parse diagnostics.
 * Stops pipeline execution if there are parse errors.
 */
public final class ParsePhase implements Phase {
    
    @Override
    public String name() {
        return "PARSE";
    }
    
    @Override
    public boolean stopOnError() {
        return true;  // Don't continue if parse fails
    }
    
    @Override
    public PhaseResult execute(PipelineContext context, PipelineModel model) {
        List<Input> inputs = context.inputs();
        
        // Resolve includes if enabled
        if (context.includeResolution()) {
            Diagnostics includeDiags = new Diagnostics();
            inputs = IncludeResolver.buildIncludeOrder(
                inputs,
                context.workingDir(),
                includeDiags
            );
            
            if (includeDiags.hasError()) {
                return PhaseResult.stopExecution(name(), includeDiags);
            }
        }
        
        // Parse all inputs
        var args = new KafkaSqlArgs(
            context.workingDir(),
            false,  // Includes already resolved above
            context.verbose()
        );
        
        ParseResult result = KafkaSqlParser.parse(inputs, args);
        
        model.setParseResult(result);
        
        // If parse errors, stop pipeline
        if (result.diags().hasError()) {
            return PhaseResult.stopExecution(name(), result.diags());
        }
        
        return PhaseResult.withDiagnostics(name(), result.diags());
    }
}
