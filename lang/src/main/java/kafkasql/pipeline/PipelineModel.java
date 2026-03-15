package kafkasql.pipeline;

import kafkasql.lang.ParseResult;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.semantic.SemanticModel;

/**
 * Mutable model that accumulates state as phases execute.
 * 
 * <p>Each phase reads from and writes to this model.
 * For now, model is recreated on each pipeline execution.
 * 
 * <p>Future: Model will be durable/persistent for incremental compilation.
 */
public final class PipelineModel {
    
    private ParseResult parseResult;
    private SemanticModel semanticModel;
    private final Diagnostics diagnostics;
    
    public PipelineModel() {
        this.diagnostics = new Diagnostics();
    }
    
    // Getters
    
    public ParseResult parseResult() {
        return parseResult;
    }
    
    public SemanticModel semanticModel() {
        return semanticModel;
    }
    
    public Diagnostics diagnostics() {
        return diagnostics;
    }
    
    // Setters (public so phases in subpackages can access)
    
    public void setParseResult(ParseResult result) {
        this.parseResult = result;
    }
    
    public void setSemanticModel(SemanticModel model) {
        this.semanticModel = model;
    }
}
