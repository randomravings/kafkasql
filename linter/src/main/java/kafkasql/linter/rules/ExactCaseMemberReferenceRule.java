package kafkasql.linter.rules;

import kafkasql.runtime.diagnostics.DiagnosticEntry.Severity;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.TypeDecl;
import kafkasql.lang.syntax.ast.literal.UnionLiteralNode;
import kafkasql.linter.LintContext;
import kafkasql.linter.LintRule;
import kafkasql.linter.RuleMetadata;
import kafkasql.runtime.type.UnionType;

/**
 * Enforces exact case matching when referencing union members in literals.
 * 
 * <p>While the language is case-insensitive for lookups, this rule ensures that
 * references use the exact casing as defined in the union declaration for consistency.
 * 
 * <p>Example violations:
 * <pre>
 * CREATE TYPE MyUnion AS UNION (
 *     ScalarValue INT32,
 *     EnumValue Status
 * );
 * 
 * // BAD: case doesn't match definition
 * MyUnion$scalarValue(42)
 * MyUnion$enumValue(Status::ACTIVE)
 * 
 * // GOOD: exact case match
 * MyUnion$ScalarValue(42)
 * MyUnion$EnumValue(Status::ACTIVE)
 * </pre>
 */
public final class ExactCaseMemberReferenceRule implements LintRule {
    
    private static final RuleMetadata METADATA = new RuleMetadata(
        "exact-case-member-reference",
        "Naming",
        "Union member references should match the exact casing from the type definition",
        Severity.WARNING
    );
    
    @Override
    public RuleMetadata metadata() {
        return METADATA;
    }
    
    @Override
    public void analyze(AstNode node, LintContext ctx) {
        if (!(node instanceof UnionLiteralNode ul)) {
            return;
        }
        
        UnionType unionType = null;
        
        // Try to get the UnionType directly from the binding of this literal node
        var unionValue = ctx.bindings().getOrNull(ul, kafkasql.runtime.value.UnionValue.class);
        if (unionValue != null) {
            unionType = unionValue.type();
        } else {
            // Fallback: Look up by resolving the type name from the AST
            // The union name might be unqualified, so we need to find it
            String typeName = ul.unionName().fullName();
            
            // First try direct lookup (in case it's fully qualified)
            var typeDeclOpt = ctx.lookupType(kafkasql.runtime.Name.of(typeName));
            
            // If not found and name is unqualified, search all types for matching simple name
            if (typeDeclOpt.isEmpty() && !typeName.contains(".")) {
                typeDeclOpt = ctx.symbols()._decl.values().stream()
                    .filter(d -> d instanceof TypeDecl)
                    .map(d -> (TypeDecl) d)
                    .filter(t -> t.name().name().equals(typeName))
                    .findFirst();
            }
            
            if (typeDeclOpt.isEmpty()) {
                return;
            }
            
            var typeDecl = typeDeclOpt.get();
            unionType = ctx.bindings().getOrNull(typeDecl, UnionType.class);
            if (unionType == null) {
                return;
            }
        }
        
        // Get the member name used in the literal
        String usedName = ul.memberName().name();
        
        // Find the actual member definition in the union (case-insensitive)
        var memberEntry = unionType.members().entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(usedName))
            .findFirst()
            .orElse(null);
        
        if (memberEntry == null) {
            return; // Semantic error, not our concern
        }
        
        String definedName = memberEntry.getKey();
        
        // Check if the casing matches exactly
        if (!usedName.equals(definedName)) {
            ctx.report(
                ul.memberName().range(),
                String.format(
                    "Union member reference '%s' should match the exact casing '%s' from the type definition",
                    usedName,
                    definedName
                )
            );
        }
    }
}
