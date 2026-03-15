package kafkasql.linter.rules;

import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.EnumSymbolDecl;
import kafkasql.linter.LintContext;
import kafkasql.linter.LintRule;
import kafkasql.linter.RuleMetadata;

/**
 * Enforces SCREAMING_SNAKE_CASE naming for enum symbols.
 * 
 * <p>Examples:
 * <ul>
 *   <li>✓ ACTIVE
 *   <li>✓ USER_STATUS
 *   <li>✓ HTTP_200_OK
 *   <li>✗ Active (should be uppercase)
 *   <li>✗ active (should be uppercase)
 *   <li>✗ Active-Status (should use underscores, not hyphens)
 * </ul>
 */
public final class ScreamingSnakeCaseEnumsRule implements LintRule {
    
    private static final RuleMetadata METADATA = new RuleMetadata(
        "screaming-snake-case-enums",
        "naming",
        "Enum symbols should use SCREAMING_SNAKE_CASE",
        DiagnosticEntry.Severity.WARNING
    );
    
    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }
    
    @Override
    public void analyze(AstNode node, LintContext ctx) {
        if (!(node instanceof EnumSymbolDecl symbol)) {
            return;
        }
        
        String name = symbol.name().name();
        if (!isScreamingSnakeCase(name)) {
            ctx.report(
                symbol.name().range(),
                "Enum symbol '" + name + "' should use SCREAMING_SNAKE_CASE (e.g., 'ACTIVE', 'USER_STATUS')"
            );
        }
    }
    
    private boolean isScreamingSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        // Should only contain uppercase letters, digits, and underscores
        for (char c : name.toCharArray()) {
            if (!Character.isUpperCase(c) && !Character.isDigit(c) && c != '_') {
                return false;
            }
        }
        
        // Should not start or end with underscore
        if (name.startsWith("_") || name.endsWith("_")) {
            return false;
        }
        
        // Should not have consecutive underscores
        if (name.contains("__")) {
            return false;
        }
        
        return true;
    }
}
