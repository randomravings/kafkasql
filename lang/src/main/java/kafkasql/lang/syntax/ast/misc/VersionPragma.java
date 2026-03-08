package kafkasql.lang.syntax.ast.misc;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

/**
 * Represents a SET VERSION = N or SET VERSION = LATEST pragma.
 * 
 * This declares which grammar version the schema file targets.
 * When LATEST is used, version is set to -1 (resolved at compile time).
 */
public final record VersionPragma(
    Range range,
    int version
) implements AstNode {
    
    /**
     * Sentinel value for SET VERSION = LATEST.
     */
    public static final int LATEST = -1;
    
    public boolean isLatest() {
        return version == LATEST;
    }
}
