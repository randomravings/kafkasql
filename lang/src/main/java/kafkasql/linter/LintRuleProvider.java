package kafkasql.linter;

import java.util.List;

/**
 * Provider interface for lint rule plugins.
 * 
 * <p>Implementations should be registered via ServiceLoader:
 * <pre>
 * META-INF/services/kafkasql.linter.LintRuleProvider
 * com.example.MyRulesProvider
 * </pre>
 * 
 * @apiNote This interface enables external plugins. It is stable across versions.
 */
public interface LintRuleProvider {
    
    /**
     * Returns the provider name (e.g., "builtin", "company-rules").
     */
    String providerName();
    
    /**
     * Returns the list of lint rules provided by this plugin.
     */
    List<LintRule> rules();
}
