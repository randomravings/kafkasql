package kafkasql.pipeline.phases;

import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.linter.LintEngine;
import kafkasql.pipeline.Phase;
import kafkasql.pipeline.PhaseResult;
import kafkasql.pipeline.PipelineContext;
import kafkasql.pipeline.PipelineModel;

/**
 * Lint phase: Style checking and best practices validation.
 * 
 * <p>Produces lint warnings but does not stop pipeline execution.
 * Warnings are advisory only.
 */
public final class LintPhase implements Phase {
    
    private final LintEngine linter;
    
    public LintPhase() {
        this.linter = new LintEngine();
    }
    
    public LintPhase(LintEngine linter) {
        this.linter = linter;
    }
    
    @Override
    public String name() {
        return "LINT";
    }
    
    @Override
    public boolean stopOnError() {
        return false;  // Continue even if lint warnings
    }
    
    @Override
    public PhaseResult execute(PipelineContext context, PipelineModel model) {
        SemanticModel semanticModel = model.semanticModel();
        if (semanticModel == null) {
            throw new IllegalStateException("SemanticPhase must run before LintPhase");
        }
        
        var parseResult = model.parseResult();
        
        Diagnostics lintDiags = linter.lint(
            parseResult.scripts(),
            semanticModel.symbols(),
            semanticModel.bindings()
        );
        
        return PhaseResult.withDiagnostics(name(), lintDiags);
    }
}
