package kafkasql.pipeline;

import java.util.ArrayList;
import java.util.List;

import kafkasql.runtime.diagnostics.Diagnostics;

/**
 * Immutable, reusable pipeline that executes phases in sequence.
 * 
 * <p>Create once via {@link #builder()}, reuse for multiple executions.
 * Each execution creates a fresh {@link PipelineModel}.
 * 
 * <p>Example:
 * <pre>
 * var pipeline = Pipeline.builder()
 *     .addPhase(new ParsePhase())
 *     .addPhase(new SemanticPhase())
 *     .addPhase(new LintPhase())
 *     .build();
 * 
 * var result = pipeline.execute(context);
 * </pre>
 */
public final class Pipeline {
    
    private final List<Phase> phases;
    
    private Pipeline(List<Phase> phases) {
        this.phases = List.copyOf(phases);
    }
    
    /**
     * Execute the pipeline with given context.
     * Creates a fresh model for this execution.
     * 
     * @param context immutable context with inputs and config
     * @return result containing model and diagnostics
     */
    public PipelineResult execute(PipelineContext context) {
        return execute(context, new PipelineModel());
    }
    
    /**
     * Execute the pipeline with given context and existing model.
     * Useful for incremental compilation (future).
     * 
     * @param context immutable context with inputs and config
     * @param model existing model to update
     * @return result containing updated model and diagnostics
     */
    public PipelineResult execute(PipelineContext context, PipelineModel model) {
        Diagnostics allDiagnostics = new Diagnostics();
        boolean completed = true;
        
        for (Phase phase : phases) {
            if (context.verbose()) {
                System.err.println("[Pipeline] Executing phase: " + phase.name());
            }
            
            PhaseResult result = phase.execute(context, model);
            
            // Merge phase diagnostics into overall diagnostics
            result.diagnostics().all().forEach(d -> {
                switch (d.severity()) {
                    case INFO -> allDiagnostics.info(d.range(), d.kind(), d.code(), d.message());
                    case WARNING -> allDiagnostics.warning(d.range(), d.kind(), d.code(), d.message());
                    case ERROR -> allDiagnostics.error(d.range(), d.kind(), d.code(), d.message());
                    case FATAL -> allDiagnostics.fatal(d.range(), d.kind(), d.code(), d.message());
                }
            });
            
            // Check if we should stop
            if (!result.shouldContinue()) {
                if (context.verbose()) {
                    System.err.println("[Pipeline] Stopped at phase: " + phase.name());
                }
                completed = false;
                break;
            }
            
            // Check if phase wants to stop on error
            if (phase.stopOnError() && allDiagnostics.hasError()) {
                if (context.verbose()) {
                    System.err.println("[Pipeline] Stopping due to errors in phase: " + phase.name());
                }
                completed = false;
                break;
            }
        }
        
        return new PipelineResult(model, allDiagnostics, completed);
    }
    
    /**
     * Creates a new pipeline builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for constructing pipelines.
     */
    public static class Builder {
        private final List<Phase> phases = new ArrayList<>();
        
        /**
         * Add a phase to the pipeline.
         * Phases execute in the order they are added.
         */
        public Builder addPhase(Phase phase) {
            phases.add(phase);
            return this;
        }
        
        /**
         * Build the immutable pipeline.
         */
        public Pipeline build() {
            if (phases.isEmpty()) {
                throw new IllegalStateException("Pipeline must have at least one phase");
            }
            return new Pipeline(phases);
        }
    }
}
