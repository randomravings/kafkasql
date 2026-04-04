package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.misc.VersionPragma;
import kafkasql.runtime.diagnostics.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/**
 * Top-level result of comparing two {@link kafkasql.lang.syntax.ast.Script}s.
 *
 * <h2>Two-phase use</h2>
 * <ol>
 *   <li><b>Syntactic diff</b> — produced by {@link AstDiff#compare}.  Every
 *       {@link StmtDiff} and {@link MemberDiff} already carries both AST nodes
 *       with their {@link Range}s, so source navigation works without enrichment.</li>
 *   <li><b>Semantic enrichment</b> (optional) — call
 *       {@link SemanticEnricher#enrich(ScriptDiff)} to populate
 *       {@link SemanticNote} lists with severity and human-readable messages.</li>
 * </ol>
 *
 * <h2>LSP consumption</h2>
 * Call {@link #flatten()} to get a {@link List}&lt;{@link DiffEntry}&gt; where
 * each entry has a {@link Range} pointing into the left or right source file and
 * a {@link DiffSeverity} that maps directly to an LSP DiagnosticSeverity.
 *
 * <p>Unchanged statements are omitted from the flattened output by default;
 * pass {@code true} to {@link #flatten(boolean)} to include them.
 *
 * @param version   Change to the {@code SET VERSION} pragma, if any.
 * @param includes  Per-include-path diff (matched by path string).
 * @param statements Per-statement diff.
 */
public record ScriptDiff(
    Optional<FieldChange> version,
    List<MemberDiff<Include>> includes,
    List<StmtDiff> statements
) {

    // =========================================================================
    // Flatten
    // =========================================================================

    /** Flatten to LSP-ready entries, omitting UNCHANGED items. */
    public List<DiffEntry> flatten() {
        return flatten(false);
    }

    /**
     * Flatten to LSP-ready entries.
     *
     * @param includeUnchanged when {@code true}, UNCHANGED entries are included at
     *                         {@link DiffSeverity#INFO} severity.
     */
    public List<DiffEntry> flatten(boolean includeUnchanged) {
        List<DiffEntry> result = new ArrayList<>();

        // Version pragma
        version.ifPresent(vc ->
            result.add(new DiffEntry(
                DiffKind.MODIFIED,
                rangeOf(vc.left()), rangeOf(vc.right()),
                DiffSeverity.WARNING, "version", "Script version changed"))
        );

        // Includes
        for (MemberDiff<Include> inc : includes) {
            if (inc.kind() == DiffKind.UNCHANGED && !includeUnchanged) continue;
            result.add(new DiffEntry(
                inc.kind(),
                rangeOf(inc.left()), rangeOf(inc.right()),
                inc.kind() == DiffKind.UNCHANGED ? DiffSeverity.INFO :
                    inc.kind() == DiffKind.LEFT_ONLY ? DiffSeverity.WARNING : DiffSeverity.INFO,
                "include",
                switch (inc.kind()) {
                    case LEFT_ONLY  -> "Include '" + inc.left().path()  + "' removed";
                    case RIGHT_ONLY -> "Include '" + inc.right().path() + "' added";
                    default         -> "Include unchanged";
                }
            ));
        }

        // Statements
        for (StmtDiff stmt : statements) {
            if (stmt.kind() == DiffKind.UNCHANGED && !includeUnchanged) continue;
            flattenStmt(stmt, result);
        }
        return List.copyOf(result);
    }

    // =========================================================================
    // Statement flattening
    // =========================================================================

    private static void flattenStmt(StmtDiff stmt, List<DiffEntry> out) {
        switch (stmt) {
            case StmtDiff.CreateDiff cd -> flattenCreate(cd, out);
            default                     -> flattenGenericStmt(stmt, out);
        }
    }

    private static void flattenCreate(StmtDiff.CreateDiff diff, List<DiffEntry> out) {
        if (diff.kind() == DiffKind.UNCHANGED) {
            // Only emitted when includeUnchanged=true
            out.add(new DiffEntry(DiffKind.UNCHANGED,
                rangeOf(diff.left()), rangeOf(diff.right()),
                DiffSeverity.INFO, "statement", "Unchanged"));
            return;
        }

        if (diff.kind() != DiffKind.MODIFIED) {
            // LEFT_ONLY or RIGHT_ONLY — whole statement added/removed
            emitStmtEntries(diff.kind(), rangeOf(diff.left()), rangeOf(diff.right()),
                diff.notes(), "statement",
                diff.kind() == DiffKind.LEFT_ONLY ? "Declaration removed" : "Declaration added",
                out);
            return;
        }

        // MODIFIED — statement-level notes first (e.g. kind-change note)
        for (SemanticNote note : diff.notes()) {
            out.add(new DiffEntry(DiffKind.MODIFIED,
                rangeOf(diff.left()), rangeOf(diff.right()),
                note.severity(), note.aspect(), note.message()));
        }

        // Recurse into DeclDiff for member-level entries
        if (diff.declDiff() != null) {
            flattenDeclDiff(diff.declDiff(), rangeOf(diff.left()), rangeOf(diff.right()), out);
        }
    }

    private static void flattenDeclDiff(DeclDiff decl, Range stmtLeft, Range stmtRight, List<DiffEntry> out) {
        switch (decl) {
            case DeclDiff.StructDiff sd -> {
                for (var md : sd.fields()) flattenMemberDiff(md, out);
            }
            case DeclDiff.EnumDiff ed -> {
                for (var change : ed.baseChanges()) {
                    out.add(entryForChange(change, DiffSeverity.BREAKING, "Enum base type changed"));
                }
                for (var md : ed.symbols()) flattenMemberDiff(md, out);
            }
            case DeclDiff.UnionDiff ud -> {
                for (var md : ud.members()) flattenMemberDiff(md, out);
            }
            case DeclDiff.StreamDiff sd -> {
                for (var md : sd.members()) flattenMemberDiff(md, out);
            }
            case DeclDiff.ScalarDiff sd -> {
                for (var change : sd.changes()) {
                    out.add(entryForChange(change, DiffSeverity.INFO, "'" + change.aspect() + "' changed"));
                }
            }
            case DeclDiff.DerivedDiff dd -> {
                for (var change : dd.changes()) {
                    out.add(entryForChange(change, DiffSeverity.INFO, "Derived type target changed"));
                }
            }
            case DeclDiff.ContextDiff cd -> {
                for (var change : cd.changes()) {
                    out.add(entryForChange(change, DiffSeverity.INFO, "Context '" + change.aspect() + "' changed"));
                }
            }
            case DeclDiff.KindChangeDiff kd -> {
                out.add(new DiffEntry(DiffKind.MODIFIED,
                    rangeOf(kd.left()), rangeOf(kd.right()),
                    DiffSeverity.BREAKING, "kind", "Declaration kind changed"));
            }
        }
    }

    private static <T extends AstNode> void flattenMemberDiff(MemberDiff<T> md, List<DiffEntry> out) {
        if (md.kind() == DiffKind.UNCHANGED) return;

        Range lr = rangeOf(md.left());
        Range rr = rangeOf(md.right());

        if (md.kind() == DiffKind.LEFT_ONLY || md.kind() == DiffKind.RIGHT_ONLY) {
            emitStmtEntries(md.kind(), lr, rr, md.notes(), "member",
                md.kind() == DiffKind.LEFT_ONLY ? "Member removed" : "Member added",
                out);
            return;
        }

        // MODIFIED — emit per FieldChange, resolved against notes by aspect
        Map<String, SemanticNote> noteByAspect = noteIndex(md.notes());

        for (FieldChange change : md.changes()) {
            Range cl = change.left()  == null ? lr : rangeOf(change.left());
            Range cr = change.right() == null ? rr : rangeOf(change.right());
            SemanticNote note = noteByAspect.get(change.aspect());
            if (note != null) {
                out.add(new DiffEntry(DiffKind.MODIFIED, cl, cr,
                    note.severity(), note.aspect(), note.message()));
            } else {
                out.add(new DiffEntry(DiffKind.MODIFIED, cl, cr,
                    DiffSeverity.INFO, change.aspect(), "'" + change.aspect() + "' changed"));
            }
        }

        // Notes without a corresponding FieldChange (e.g. from semantic-only observations)
        for (SemanticNote note : md.notes()) {
            boolean hasChange = md.changes().stream().anyMatch(c -> c.aspect().equals(note.aspect()));
            if (!hasChange) {
                out.add(new DiffEntry(DiffKind.MODIFIED, lr, rr,
                    note.severity(), note.aspect(), note.message()));
            }
        }
    }

    private static void flattenGenericStmt(StmtDiff stmt, List<DiffEntry> out) {
        Range lr = stmtLeftRange(stmt);
        Range rr = stmtRightRange(stmt);
        emitStmtEntries(stmt.kind(), lr, rr, stmt.notes(), "statement",
            defaultMessage(stmt.kind()), out);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void emitStmtEntries(
        DiffKind kind, Range lr, Range rr,
        List<SemanticNote> notes, String fallbackAspect, String fallbackMsg,
        List<DiffEntry> out
    ) {
        if (notes.isEmpty()) {
            out.add(new DiffEntry(kind, lr, rr, DiffSeverity.INFO, fallbackAspect, fallbackMsg));
        } else {
            for (SemanticNote note : notes) {
                out.add(new DiffEntry(kind, lr, rr, note.severity(), note.aspect(), note.message()));
            }
        }
    }

    private static DiffEntry entryForChange(FieldChange change, DiffSeverity fallback, String fallbackMsg) {
        Range lr = rangeOf(change.left());
        Range rr = rangeOf(change.right());
        return new DiffEntry(DiffKind.MODIFIED, lr, rr, fallback, change.aspect(), fallbackMsg);
    }

    private static Map<String, SemanticNote> noteIndex(List<SemanticNote> notes) {
        Map<String, SemanticNote> map = new HashMap<>();
        for (SemanticNote n : notes) map.put(n.aspect(), n);
        return map;
    }

    private static Range rangeOf(AstNode node) {
        return node == null ? null : node.range();
    }

    private static Range stmtLeftRange(StmtDiff stmt) {
        return switch (stmt) {
            case StmtDiff.AlterDiff   d -> rangeOf(d.left());
            case StmtDiff.DropDiff    d -> rangeOf(d.left());
            case StmtDiff.UseDiff     d -> rangeOf(d.left());
            case StmtDiff.ReadDiff    d -> rangeOf(d.left());
            case StmtDiff.WriteDiff   d -> rangeOf(d.left());
            case StmtDiff.ShowDiff    d -> rangeOf(d.left());
            case StmtDiff.ExplainDiff d -> rangeOf(d.left());
            default -> null;
        };
    }

    private static Range stmtRightRange(StmtDiff stmt) {
        return switch (stmt) {
            case StmtDiff.AlterDiff   d -> rangeOf(d.right());
            case StmtDiff.DropDiff    d -> rangeOf(d.right());
            case StmtDiff.UseDiff     d -> rangeOf(d.right());
            case StmtDiff.ReadDiff    d -> rangeOf(d.right());
            case StmtDiff.WriteDiff   d -> rangeOf(d.right());
            case StmtDiff.ShowDiff    d -> rangeOf(d.right());
            case StmtDiff.ExplainDiff d -> rangeOf(d.right());
            default -> null;
        };
    }

    private static String defaultMessage(DiffKind kind) {
        return switch (kind) {
            case LEFT_ONLY  -> "Statement removed";
            case RIGHT_ONLY -> "Statement added";
            case MODIFIED   -> "Statement changed";
            case UNCHANGED  -> "Unchanged";
        };
    }
}
