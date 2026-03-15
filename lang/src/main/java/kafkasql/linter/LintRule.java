package kafkasql.linter;

import kafkasql.lang.syntax.ast.AstNode;

/**
 * A lint rule that analyzes code for style, conventions, or potential issues.
 * 
 * <p>This interface is designed to be stable across versions and support
 * external plugins via ServiceLoader.
 * 
 * <p>Rules should be stateless and thread-safe. Each invocation of
 * {@link #analyze} should be independent.
 * 
 * @apiNote This is a stable API. Breaking changes only in major versions.
 */
public interface LintRule {
    
    /**
     * Returns metadata about this rule including ID, description, and default severity.
     */
    RuleMetadata metadata();
    
    /**
     * Analyzes an AST node and reports any issues via the context.
     * 
     * <p>This method is called for every node in the AST during linting.
     * Rules should check the node type and only process relevant nodes.
     * 
     * @param node the AST node to analyze
     * @param ctx context for reporting issues and accessing semantic info
     */
    void analyze(AstNode node, LintContext ctx);
}
