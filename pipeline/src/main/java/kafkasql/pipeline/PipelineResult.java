package kafkasql.pipeline;

import kafkasql.runtime.diagnostics.Diagnostics;

/**
 * Final result of executing the entire pipeline.
 * 
 * <p>Contains all diagnostics from all phases and the final model state.
 */
public record PipelineResult(
    PipelineModel model,
    Diagnostics diagnostics,
    boolean completed
) {
    
    /**
     * Whether the pipeline completed successfully (no errors).
     */
    public boolean succeeded() {
        return !diagnostics.hasError();
    }
    
    /**
     * Whether the pipeline was stopped early due to errors.
     */
    public boolean stoppedEarly() {
        return !completed;
    }
}
