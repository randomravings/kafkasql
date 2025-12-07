package kafkasql.pipeline;

import kafkasql.runtime.diagnostics.Diagnostics;

/**
 * Result of executing a single phase.
 * 
 * <p>Contains diagnostics and a flag indicating whether to continue.
 * If {@code shouldContinue} is false, the pipeline stops execution.
 */
public record PhaseResult(
    String phaseName,
    Diagnostics diagnostics,
    boolean shouldContinue
) {
    
    /**
     * Creates a successful result that continues to next phase.
     */
    public static PhaseResult success(String phaseName) {
        return new PhaseResult(phaseName, new Diagnostics(), true);
    }
    
    /**
     * Creates a result with diagnostics that continues to next phase.
     */
    public static PhaseResult withDiagnostics(String phaseName, Diagnostics diagnostics) {
        return new PhaseResult(phaseName, diagnostics, true);
    }
    
    /**
     * Creates a result that stops pipeline execution.
     */
    public static PhaseResult stopExecution(String phaseName, Diagnostics diagnostics) {
        return new PhaseResult(phaseName, diagnostics, false);
    }
}
