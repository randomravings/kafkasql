package kafkasql.lang;

import kafkasql.lang.syntax.ast.misc.VersionPragma;

import java.util.Optional;

/**
 * Tracks the grammar version of the KafkaSQL language.
 * 
 * This is used for forward/backward compatibility when persisting and replaying
 * DDL events. Each event records which grammar version produced it, so that
 * readers can select the appropriate parser or apply compatibility transforms.
 * 
 * Schema files declare their target version with:
 *   SET VERSION = 1;    -- targets specific version
 *   SET VERSION = LATEST; -- targets current version (for development)
 * 
 * If no SET VERSION pragma is present, LATEST is assumed.
 * 
 * Version History:
 *   1 - Initial grammar (2025-12)
 *       Supports: CONTEXT, TYPE (SCALAR, ENUM, STRUCT, UNION), STREAM
 *       Statements: CREATE, USE, SHOW, EXPLAIN, READ, WRITE
 * 
 * Compatibility Policy:
 * - Minor additions (new keywords, new type kinds) increment the version
 * - The reader must be able to handle events from older grammar versions
 * - Breaking changes that cannot be handled should use a new stream
 */
public final class GrammarVersion {
    
    private GrammarVersion() {}
    
    /**
     * The current grammar version.
     * Increment this when the grammar changes in a way that affects
     * how Delta/State strings in events are parsed.
     */
    public static final int CURRENT = 1;
    
    /**
     * Resolves the effective grammar version from a parsed VersionPragma.
     * 
     * @param pragma The version pragma from the script (may be empty if not declared)
     * @return The resolved integer version
     */
    public static int resolve(Optional<VersionPragma> pragma) {
        if (pragma.isEmpty()) {
            return CURRENT;  // Default when no SET VERSION declared
        }
        VersionPragma vp = pragma.get();
        return vp.isLatest() ? CURRENT : vp.version();
    }
}
