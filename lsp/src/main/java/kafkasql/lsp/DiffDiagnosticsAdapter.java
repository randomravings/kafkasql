package kafkasql.lsp;

import kafkasql.lang.compare.DiffEntry;
import kafkasql.lang.compare.DiffKind;
import kafkasql.lang.compare.DiffSeverity;
import kafkasql.runtime.diagnostics.Range;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a list of {@link DiffEntry} objects (produced by
 * {@link kafkasql.lang.compare.ScriptDiff#flatten()}) into LSP
 * {@link Diagnostic}s that VS Code can display as squiggle underlines.
 *
 * <h2>Diagnostic placement strategy</h2>
 * <ul>
 *   <li><b>MODIFIED</b> — diagnostic anchored to {@code rightRange} (the current
 *       file, i.e. the "after" version in the editor buffer).</li>
 *   <li><b>LEFT_ONLY</b> (declaration/member removed) — no location in the current
 *       file; diagnostic placed at the top of the file (line 0, char 0) so the
 *       developer knows something was deleted.</li>
 *   <li><b>RIGHT_ONLY</b> (newly added) — generally a safe operation; emitted only
 *       for BREAKING/WARNING severity (unusual but possible via custom rules).</li>
 *   <li><b>UNCHANGED</b> — always filtered out.</li>
 *   <li>{@link DiffSeverity#INFO} — always filtered out (not actionable).</li>
 * </ul>
 *
 * <h2>Source tag</h2>
 * All diagnostics produced here use the source {@code "kafkasql-compat"} so
 * they are visually distinct from ordinary parse/semantic errors
 * ({@code "kafkasql"}).
 */
public final class DiffDiagnosticsAdapter {

    private static final String SOURCE = "kafkasql-compat";

    private DiffDiagnosticsAdapter() {}

    /**
     * Convert diff entries to LSP diagnostics.
     *
     * @param entries  flattened diff entries from {@link kafkasql.lang.compare.ScriptDiff#flatten()}
     * @return LSP diagnostics ready for {@code publishDiagnostics}; may be empty
     */
    public static List<Diagnostic> toDiagnostics(List<DiffEntry> entries) {
        List<Diagnostic> result = new ArrayList<>();

        for (DiffEntry entry : entries) {
            // INFO-level and UNCHANGED diffs are not actionable — skip
            if (entry.severity() == DiffSeverity.INFO) continue;
            if (entry.kind() == DiffKind.UNCHANGED) continue;

            org.eclipse.lsp4j.Range lspRange = resolveRange(entry);
            DiagnosticSeverity lspSeverity   = mapSeverity(entry.severity());
            String message                   = buildMessage(entry);

            Diagnostic diag = new Diagnostic(lspRange, message);
            diag.setSeverity(lspSeverity);
            diag.setSource(SOURCE);
            result.add(diag);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Pick the best source location for the diagnostic in the current (right) file.
     *
     * <ul>
     *   <li>MODIFIED / RIGHT_ONLY → use {@code rightRange}.</li>
     *   <li>LEFT_ONLY (removed) → no location in the current file; fall back to
     *       the top of file (0:0) so VS Code has somewhere to anchor the squiggle.</li>
     * </ul>
     */
    private static org.eclipse.lsp4j.Range resolveRange(DiffEntry entry) {
        Range r = null;

        if (entry.kind() == DiffKind.LEFT_ONLY) {
            // Removed from the current file — anchor to top of file
            return zeroRange();
        }

        // For MODIFIED and RIGHT_ONLY prefer the right (current file) range
        r = entry.rightRange();
        if (r == null || r == Range.NONE) {
            // Fall back to left range if right is absent (shouldn't normally happen)
            r = entry.leftRange();
        }
        if (r == null || r == Range.NONE) {
            return zeroRange();
        }

        int startLine = Math.max(0, r.from().ln() - 1);
        int startChar = Math.max(0, r.from().ch());
        int endLine   = Math.max(startLine, r.to().ln() - 1);
        int endChar   = Math.max(0, r.to().ch());

        return new org.eclipse.lsp4j.Range(
            new Position(startLine, startChar),
            new Position(endLine,   endChar));
    }

    private static org.eclipse.lsp4j.Range zeroRange() {
        Position p = new Position(0, 0);
        return new org.eclipse.lsp4j.Range(p, p);
    }

    private static DiagnosticSeverity mapSeverity(DiffSeverity severity) {
        return switch (severity) {
            case BREAKING -> DiagnosticSeverity.Error;
            case WARNING  -> DiagnosticSeverity.Warning;
            case SAFE     -> DiagnosticSeverity.Information;
            case INFO     -> DiagnosticSeverity.Hint;
        };
    }

    private static String buildMessage(DiffEntry entry) {
        String prefix = switch (entry.kind()) {
            case LEFT_ONLY  -> "Removed";
            case RIGHT_ONLY -> "Added";
            case MODIFIED   -> "Changed";
            case UNCHANGED  -> "";
        };
        // e.g. "Changed type: INT32 → INT64  [struct.field / type]"
        // The DiffEntry message is already human-readable; we annotate with aspect
        // and the compatibility level so the developer can see it at a glance.
        String compatLabel = switch (entry.severity()) {
            case BREAKING -> "[BREAKING]";
            case WARNING  -> "[WARNING]";
            case SAFE     -> "[SAFE]";
            case INFO     -> "[INFO]";
        };
        return compatLabel + " " + prefix + ": " + entry.message();
    }
}
