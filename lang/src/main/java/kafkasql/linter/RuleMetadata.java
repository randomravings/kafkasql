package kafkasql.linter;

import kafkasql.runtime.diagnostics.DiagnosticEntry;

/**
 * Metadata describing a lint rule.
 * 
 * @param id unique identifier for the rule (e.g., "naming/pascal-case-types")
 * @param category rule category (e.g., "naming", "complexity", "style")
 * @param description human-readable description
 * @param defaultSeverity default severity if not configured
 */
public record RuleMetadata(
    String id,
    String category,
    String description,
    DiagnosticEntry.Severity defaultSeverity
) {
    public RuleMetadata {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rule ID cannot be null or blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Rule category cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Rule description cannot be null or blank");
        }
        if (defaultSeverity == null) {
            throw new IllegalArgumentException("Rule default severity cannot be null");
        }
    }
    
    /**
     * Returns the full qualified rule ID (category/id).
     */
    public String qualifiedId() {
        return category + "/" + id;
    }
}
