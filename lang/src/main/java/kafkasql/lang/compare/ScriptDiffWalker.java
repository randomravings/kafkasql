package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.misc.Include;

import java.util.List;

/**
 * Drives a {@link ScriptDiffVisitor} over a {@link ScriptDiff} tree.
 *
 * <p>Both the CLI ({@code DiffPrinter}) and the LSP can implement
 * {@link ScriptDiffVisitor} — this class holds the single canonical traversal so
 * neither consumer needs to duplicate the walk logic.
 *
 * <p>Typical use:
 * <pre>{@code
 * ScriptDiffVisitor printer = new DiffPrinter(out, useColor, showUnchanged);
 * ScriptDiffWalker.walk(diff, printer);
 * }</pre>
 */
public final class ScriptDiffWalker {

    private ScriptDiffWalker() {}

    /** Walk {@code diff}, omitting UNCHANGED items. */
    public static void walk(ScriptDiff diff, ScriptDiffVisitor visitor) {
        walk(diff, visitor, false);
    }

    /**
     * Walk {@code diff}, delivering events to {@code visitor}.
     *
     * @param includeUnchanged when {@code true}, UNCHANGED items are included.
     */
    public static void walk(ScriptDiff diff, ScriptDiffVisitor visitor, boolean includeUnchanged) {
        diff.version().ifPresent(visitor::onVersion);

        for (MemberDiff<Include> inc : diff.includes()) {
            if (inc.kind() == DiffKind.UNCHANGED && !includeUnchanged) continue;
            visitor.onInclude(inc);
        }

        for (StmtDiff stmt : diff.statements()) {
            if (stmt.kind() == DiffKind.UNCHANGED && !includeUnchanged) continue;
            walkStatement(stmt, visitor, includeUnchanged);
        }
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private static void walkStatement(StmtDiff stmt, ScriptDiffVisitor visitor, boolean includeUnchanged) {
        visitor.onBeginStatement(stmt);
        for (SemanticNote note : stmt.notes()) visitor.onNote(note);

        if (stmt instanceof StmtDiff.CreateDiff cd
                && cd.kind() == DiffKind.MODIFIED
                && cd.declDiff() != null) {
            walkDecl(cd.declDiff(), visitor, includeUnchanged);
        }

        visitor.onEndStatement(stmt);
    }

    // ── Decl ──────────────────────────────────────────────────────────────────

    private static void walkDecl(DeclDiff decl, ScriptDiffVisitor visitor, boolean includeUnchanged) {
        visitor.onBeginDecl(decl);

        switch (decl) {
            case DeclDiff.KindChangeDiff kc -> visitor.onKindChange(kc);
            case DeclDiff.StructDiff     sd -> walkMembers(sd.fields(),  visitor, includeUnchanged);
            case DeclDiff.EnumDiff       ed -> {
                for (FieldChange fc : ed.baseChanges()) visitor.onFieldChange(fc);
                walkMembers(ed.symbols(), visitor, includeUnchanged);
            }
            case DeclDiff.UnionDiff      ud -> walkMembers(ud.members(), visitor, includeUnchanged);
            case DeclDiff.StreamDiff     sd -> walkMembers(sd.members(), visitor, includeUnchanged);
            case DeclDiff.ScalarDiff     sd -> { for (FieldChange fc : sd.changes()) visitor.onFieldChange(fc); }
            case DeclDiff.DerivedDiff    dd -> { for (FieldChange fc : dd.changes()) visitor.onFieldChange(fc); }
            case DeclDiff.ContextDiff    cd -> { for (FieldChange fc : cd.changes()) visitor.onFieldChange(fc); }
        }

        visitor.onEndDecl(decl);
    }

    // ── Members ───────────────────────────────────────────────────────────────

    private static <T extends AstNode> void walkMembers(
            List<MemberDiff<T>> members, ScriptDiffVisitor visitor, boolean includeUnchanged) {
        for (MemberDiff<T> m : members) {
            if (m.kind() == DiffKind.UNCHANGED && !includeUnchanged) continue;
            visitor.onBeginMember(m);
            if (m.kind() == DiffKind.MODIFIED) {
                for (FieldChange fc : m.changes()) visitor.onFieldChange(fc);
            }
            for (SemanticNote note : m.notes()) visitor.onNote(note);
            visitor.onEndMember(m);
        }
    }
}
