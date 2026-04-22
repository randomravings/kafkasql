package kafkasql.lang.compare;

import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.FileInput;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.misc.VersionPragma;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.runtime.diagnostics.Range;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shared diff pipeline used by both the CLI and the LSP.
 *
 * <p>Workflow: parse → diff → (optionally) semantic enrichment.
 * The resulting {@link ScriptDiff} can be rendered in two ways:
 * <ul>
 *   <li>CLI   — pass to {@code DiffPrinter} for ANSI text output.</li>
 *   <li>LSP   — call {@link ScriptDiff#flatten()} to get a
 *       {@link List}&lt;{@link DiffEntry}&gt; that is returned as a
 *       JSON-RPC response and rendered by the VS Code extension.</li>
 * </ul>
 */
public final class ScriptDiffer {

    private ScriptDiffer() {}

    /**
     * Parse two files, compute their structural diff, and optionally enrich with
     * semantic severity notes.
     *
     * @param left             absolute path to the "old" file
     * @param right            absolute path to the "new" file
     * @param workingDir       used as the workspace root for include resolution
     * @param resolveIncludes  whether to follow {@code include} directives
     * @param semantic         whether to run semantic enrichment after diffing
     * @return the computed (and optionally enriched) diff
     * @throws IllegalArgumentException if either file is missing or contains parse errors
     */
    public static ScriptDiff diff(
            Path left, Path right,
            Path workingDir,
            boolean resolveIncludes,
            boolean semantic) {

        Script leftScript  = parse(left,  workingDir, resolveIncludes);
        Script rightScript = parse(right, workingDir, resolveIncludes);
        return diff(leftScript, rightScript, semantic);
    }

    /**
     * Parse two files, compute their structural diff, and enrich with semantic
     * severity notes using the supplied rule set.
     *
     * @param left            absolute path to the "old" file
     * @param right           absolute path to the "new" file
     * @param workingDir      used as the workspace root for include resolution
     * @param resolveIncludes whether to follow {@code include} directives
     * @param rules           the enrichment rule set to use
     * @return the computed and enriched diff
     * @throws IllegalArgumentException if either file is missing or contains parse errors
     */
    public static ScriptDiff diff(
            Path left, Path right,
            Path workingDir,
            boolean resolveIncludes,
            RuleSet rules) {

        Script leftScript  = parse(left,  workingDir, resolveIncludes);
        Script rightScript = parse(right, workingDir, resolveIncludes);
        return diff(leftScript, rightScript, rules);
    }

    /**
     * Compute the diff of two already-parsed scripts, optionally enriched.
     *
     * @param left     the "old" script
     * @param right    the "new" script
     * @param semantic whether to run semantic enrichment
     * @return the computed (and optionally enriched) diff
     */
    public static ScriptDiff diff(Script left, Script right, boolean semantic) {
        ScriptDiff result = AstDiff.compare(left, right);
        if (semantic) {
            SemanticEnricher.enrich(result);
        }
        return result;
    }

    /**
     * Compute the diff of two already-parsed scripts, enriched with a custom rule set.
     *
     * @param left   the "old" script
     * @param right  the "new" script
     * @param rules  the enrichment rule set to use
     * @return the computed and enriched diff
     */
    public static ScriptDiff diff(Script left, Script right, RuleSet rules) {
        ScriptDiff result = AstDiff.compare(left, right);
        SemanticEnricher.enrich(result, rules);
        return result;
    }

    /**
     * Parse two files and generate a minimal KafkaSQL delta script that evolves
     * the left schema to the right schema.
     *
     * <p>Only {@code CREATE} declarations are compared; all other statement types
     * are ignored in the output.  For full delta generation logic see
     * {@link DeltaScriptGenerator}.
     *
     * @param left            absolute path to the "old" file
     * @param right           absolute path to the "new" file
     * @param workingDir      used as the workspace root for include resolution
     * @param resolveIncludes whether to follow {@code include} directives
     * @return valid KafkaSQL delta source text
     * @throws IllegalArgumentException if either file is missing or contains parse errors
     */
    public static String generateDelta(
            Path left, Path right,
            Path workingDir,
            boolean resolveIncludes) {

        ScriptDiff d = diff(left, right, workingDir, resolveIncludes, false);
        return DeltaScriptGenerator.generate(d);
    }

    // -------------------------------------------------------------------------
    // In-memory overloads (no file I/O — used by LSP and tests)

    /**
     * Parse two in-memory scripts and diff them using a custom rule set.
     *
     * <p>Useful for the LSP, which already holds both the git baseline content and the
     * current editor buffer as strings — no temporary files needed.
     *
     * @param leftContent   source text of the "old" (baseline) script
     * @param leftUri       logical URI / name for the left content (used in diagnostics)
     * @param rightContent  source text of the "new" (current) script
     * @param rightUri      logical URI / name for the right content
     * @param workingDir    workspace root for include resolution
     * @param resolveIncludes whether to follow {@code include} directives
     * @param rules         the enrichment rule set to use
     * @return the computed and enriched diff
     * @throws IllegalArgumentException if either script has parse errors
     */
    public static ScriptDiff diff(
            String leftContent,  String leftUri,
            String rightContent, String rightUri,
            Path workingDir,
            boolean resolveIncludes,
            RuleSet rules) {

        // Empty content is valid (e.g. a cluster with no deployed schema).
        // Handle at the boundary so internal helpers never receive blank input.
        Script leftScript  = isBlank(leftContent)  ? emptyScript(leftUri)  : parseString(leftContent,  leftUri,  workingDir, resolveIncludes);
        Script rightScript = isBlank(rightContent) ? emptyScript(rightUri) : parseString(rightContent, rightUri, workingDir, resolveIncludes);
        return diff(leftScript, rightScript, rules);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Script emptyScript(String uri) {
        return new Script(
            Range.NONE,
            new AstListNode<>(Include.class),
            Optional.empty(),
            new AstListNode<>(Stmt.class)
        );
    }

    // -------------------------------------------------------------------------
    // Internal

    private static Script parse(Path filePath, Path workingDir, boolean resolveIncludes) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        Input input = new FileInput(filePath.toString(), filePath);
        KafkaSqlArgs args = new KafkaSqlArgs(workingDir, resolveIncludes, false);
        ParseResult result = KafkaSqlParser.parse(List.of(input), args);
        if (result.diags().hasError()) {
            String errors = result.diags().all().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n  "));
            throw new IllegalArgumentException("Parse errors in " + filePath + ":\n  " + errors);
        }
        if (result.scripts() == null || result.scripts().isEmpty()) {
            throw new IllegalArgumentException("No script produced from: " + filePath);
        }
        return result.scripts().getFirst();
    }

    private static Script parseString(String content, String uri, Path workingDir, boolean resolveIncludes) {
        Input input = new StringInput(uri, content);
        KafkaSqlArgs args = new KafkaSqlArgs(workingDir, resolveIncludes, false);
        ParseResult result = KafkaSqlParser.parse(List.of(input), args);
        if (result.diags().hasError()) {
            String errors = result.diags().all().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n  "));
            throw new IllegalArgumentException("Parse errors in '" + uri + "':\n  " + errors);
        }
        if (result.scripts() == null || result.scripts().isEmpty()) {
            throw new IllegalArgumentException("No script produced from: " + uri);
        }
        return result.scripts().getFirst();
    }
}
