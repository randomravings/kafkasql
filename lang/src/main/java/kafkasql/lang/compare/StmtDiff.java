package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Sealed hierarchy representing the diff result for a single {@link Stmt}.
 *
 * <p>Every variant carries the original left/right AST nodes so their
 * {@code .range()} can always be used — even before semantic enrichment — to locate
 * the statement in the source text for LSP features such as diff decorations or
 * diagnostic anchors.
 *
 * <p>{@code notes} on each variant is populated lazily by
 * {@link SemanticEnricher#enrich(ScriptDiff)}.
 */
public sealed interface StmtDiff
    permits StmtDiff.CreateDiff,
            StmtDiff.AlterDiff,
            StmtDiff.DropDiff,
            StmtDiff.UseDiff,
            StmtDiff.ReadDiff,
            StmtDiff.WriteDiff,
            StmtDiff.ShowDiff,
            StmtDiff.ExplainDiff
{
    DiffKind kind();
    List<SemanticNote> notes();

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Diff for a {@link CreateStmt}.
     *
     * When {@code kind == MODIFIED}, {@code declDiff} contains the full structural
     * diff of the declaration.  It is {@code null} for LEFT_ONLY / RIGHT_ONLY.
     */
    record CreateDiff(
        DiffKind kind,
        CreateStmt left,
        CreateStmt right,
        DeclDiff declDiff,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── ALTER ─────────────────────────────────────────────────────────────────

    record AlterDiff(
        DiffKind kind,
        AlterStmt left,
        AlterStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── DROP ──────────────────────────────────────────────────────────────────

    record DropDiff(
        DiffKind kind,
        DropStmt left,
        DropStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── USE ───────────────────────────────────────────────────────────────────

    record UseDiff(
        DiffKind kind,
        UseStmt left,
        UseStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── READ ──────────────────────────────────────────────────────────────────

    record ReadDiff(
        DiffKind kind,
        ReadStmt left,
        ReadStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── WRITE ─────────────────────────────────────────────────────────────────

    record WriteDiff(
        DiffKind kind,
        WriteStmt left,
        WriteStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── SHOW ──────────────────────────────────────────────────────────────────

    record ShowDiff(
        DiffKind kind,
        ShowStmt left,
        ShowStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── EXPLAIN ───────────────────────────────────────────────────────────────

    record ExplainDiff(
        DiffKind kind,
        ExplainStmt left,
        ExplainStmt right,
        List<SemanticNote> notes
    ) implements StmtDiff {}

    // ── Factories ─────────────────────────────────────────────────────────────

    static CreateDiff createLeftOnly(CreateStmt left) {
        return new CreateDiff(DiffKind.LEFT_ONLY, left, null, null, new ArrayList<>());
    }

    static CreateDiff createRightOnly(CreateStmt right) {
        return new CreateDiff(DiffKind.RIGHT_ONLY, null, right, null, new ArrayList<>());
    }
}
