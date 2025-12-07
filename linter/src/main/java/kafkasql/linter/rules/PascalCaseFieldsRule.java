package kafkasql.linter.rules;

import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.StructFieldDecl;
import kafkasql.lang.syntax.ast.decl.UnionMemberDecl;
import kafkasql.linter.LintContext;
import kafkasql.linter.LintRule;
import kafkasql.linter.RuleMetadata;

/**
 * Enforces PascalCase naming for struct fields and union members.
 * 
 * <p>This follows the convention where field/member names use PascalCase
 * similar to property names in C#/Java records.
 * 
 * <p>Examples:
 * <ul>
 *   <li>✓ FirstName
 *   <li>✓ UserId
 *   <li>✓ HTTPStatus
 *   <li>✗ firstName (should start with uppercase for consistency)
 *   <li>✗ first_name (should not use underscores)
 * </ul>
 */
public final class PascalCaseFieldsRule implements LintRule {
    
    private static final RuleMetadata METADATA = new RuleMetadata(
        "pascal-case-fields",
        "naming",
        "Struct fields and union members should use PascalCase",
        DiagnosticEntry.Severity.WARNING
    );
    
    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }
    
    @Override
    public void analyze(AstNode node, LintContext ctx) {
        String name = null;
        
        if (node instanceof StructFieldDecl field) {
            name = field.name().name();
        } else if (node instanceof UnionMemberDecl member) {
            name = member.name().name();
        } else {
            return;
        }
        
        if (!isPascalCase(name)) {
            String kind = (node instanceof StructFieldDecl) ? "Field" : "Union member";
            ctx.report(
                ((node instanceof StructFieldDecl) 
                    ? ((StructFieldDecl) node).name().range()
                    : ((UnionMemberDecl) node).name().range()),
                kind + " name '" + name + "' should use PascalCase (e.g., 'FirstName', 'UserId')"
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
        
        // Should not contain underscores or hyphens
        if (name.contains("_") || name.contains("-")) {
            return false;
        }
        
        // Should only contain letters and digits
        return name.chars().allMatch(c -> Character.isLetterOrDigit(c));
    }
}
