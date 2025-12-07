package kafkasql.linter.rules;

import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.linter.LintContext;
import kafkasql.linter.LintRule;
import kafkasql.linter.RuleMetadata;

/**
 * Enforces PascalCase naming for type declarations.
 * 
 * <p>Examples:
 * <ul>
 *   <li>✓ UserStatus
 *   <li>✓ HTTPResponse
 *   <li>✗ userStatus (should start with uppercase)
 *   <li>✗ user_status (should not contain underscores)
 * </ul>
 */
public final class PascalCaseTypesRule implements LintRule {
    
    private static final RuleMetadata METADATA = new RuleMetadata(
        "pascal-case-types",
        "naming",
        "Type names should use PascalCase",
        DiagnosticEntry.Severity.WARNING
    );
    
    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }
    
    @Override
    public void analyze(AstNode node, LintContext ctx) {
        if (!(node instanceof TypeDecl typeDecl)) {
            return;
        }
        
        String name = typeDecl.name().name();
        if (!isPascalCase(name)) {
            ctx.report(
                typeDecl.name().range(),
                "Type name '" + name + "' should use PascalCase (e.g., 'UserStatus', 'HTTPResponse')"
            );
        }
    }
    
    private boolean isPascalCase(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        // Must start with uppercase letter
        if (!Character.isUpperCase(name.charAt(0))) {
            return false;
        }
        
        // Should not contain underscores (snake_case) or hyphens
        if (name.contains("_") || name.contains("-")) {
            return false;
        }
        
        // Should only contain letters and digits
        return name.chars().allMatch(c -> Character.isLetterOrDigit(c));
    }
}
