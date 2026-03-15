package kafkasql.pipeline;

/**
 * Represents a single phase in the compilation pipeline.
 * 
 * <p>Each phase receives a context and model, performs its work,
 * and returns a result. Phases are stateless and reusable.
 * 
 * <p>Built-in phases: PARSE, SEMANTIC, LINT
 * Custom phases can be added via {@link Pipeline.Builder#addPhase(Phase)}
 */
public interface Phase {
    
    /**
     * Execute this phase.
     * 
     * @param context immutable context with inputs and configuration
     * @param model mutable model to read from and write to
     * @return result containing diagnostics and artifacts
     */
    PhaseResult execute(PipelineContext context, PipelineModel model);
    
    /**
     * Returns the name of this phase for logging/debugging.
     */
    String name();
    
    /**
     * Whether this phase should stop pipeline execution on error.
     * Default is false (continue on error).
     */
    default boolean stopOnError() {
        return false;
    }
}
