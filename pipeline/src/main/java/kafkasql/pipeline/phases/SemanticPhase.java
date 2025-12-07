package kafkasql.pipeline.phases;

import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.pipeline.Phase;
import kafkasql.pipeline.PhaseResult;
import kafkasql.pipeline.PipelineContext;
import kafkasql.pipeline.PipelineModel;

/**
 * Semantic analysis phase: Type checking, symbol resolution, binding.
 * 
 * <p>Produces {@link SemanticModel} with symbols, bindings, and semantic diagnostics.
 * Stops pipeline execution if there are semantic errors.
 */
public final class SemanticPhase implements Phase {
    
    @Override
    public String name() {
        return "SEMANTIC";
    }
    
    @Override
    public boolean stopOnError() {
        return true;  // Don't continue if semantic analysis fails
    }
    
    @Override
    public PhaseResult execute(PipelineContext context, PipelineModel model) {
        ParseResult parseResult = model.parseResult();
        if (parseResult == null) {
            throw new IllegalStateException("ParsePhase must run before SemanticPhase");
        }
        
        SemanticModel semanticModel = KafkaSqlParser.bind(parseResult);
        
        model.setSemanticModel(semanticModel);
        
        // If semantic errors, stop pipeline
        if (semanticModel.diags().hasError()) {
            return PhaseResult.stopExecution(name(), semanticModel.diags());
        }
        
        return PhaseResult.withDiagnostics(name(), semanticModel.diags());
    }
}
