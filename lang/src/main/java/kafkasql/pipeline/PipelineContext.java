package kafkasql.pipeline;

import java.nio.file.Path;
import java.util.List;

import kafkasql.lang.input.Input;
import kafkasql.linter.LintSettings;

/**
 * Immutable context passed to each phase during pipeline execution.
 * 
 * <p>Contains inputs, configuration, and working directory.
 * Phases read from this but do not modify it.
 */
public record PipelineContext(
    List<Input> inputs,
    Path workingDir,
    boolean includeResolution,
    boolean verbose,
    LintSettings lintSettings
) {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<Input> inputs;
        private Path workingDir = Path.of(".");
        private boolean includeResolution = true;
        private boolean verbose = false;
        private LintSettings lintSettings = LintSettings.defaults();
        
        public Builder inputs(List<Input> inputs) {
            this.inputs = inputs;
            return this;
        }
        
        public Builder workingDir(Path workingDir) {
            this.workingDir = workingDir;
            return this;
        }
        
        public Builder includeResolution(boolean enable) {
            this.includeResolution = enable;
            return this;
        }
        
        public Builder verbose(boolean enable) {
            this.verbose = enable;
            return this;
        }

        public Builder lintSettings(LintSettings lintSettings) {
            this.lintSettings = lintSettings != null ? lintSettings : LintSettings.defaults();
            return this;
        }
        
        public PipelineContext build() {
            if (inputs == null) {
                throw new IllegalStateException("inputs are required");
            }
            return new PipelineContext(inputs, workingDir, includeResolution, verbose, lintSettings);
        }
    }
}
