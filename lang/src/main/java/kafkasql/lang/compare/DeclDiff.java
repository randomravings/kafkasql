package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.decl.*;

import java.util.List;

/**
 * Sealed hierarchy representing the diff result for a single {@code Decl} (a type,
 * stream, or context declaration).
 *
 * <p>Each variant corresponds to one {@link kafkasql.lang.syntax.ast.decl.TypeKindDecl}
 * subtype, plus a special {@link KindChangeDiff} for when the declaration kind itself
 * changed.
 *
 * <p>Follows the same nested-record convention used by
 * {@link kafkasql.lang.syntax.ast.stmt.AlterStmt}.
 */
public sealed interface DeclDiff
    permits DeclDiff.StructDiff,
            DeclDiff.EnumDiff,
            DeclDiff.UnionDiff,
            DeclDiff.ScalarDiff,
            DeclDiff.DerivedDiff,
            DeclDiff.StreamDiff,
            DeclDiff.ContextDiff,
            DeclDiff.KindChangeDiff
{
    DiffKind kind();

    // ── Struct ────────────────────────────────────────────────────────────────

    /**
     * Diff for a {@code CREATE TYPE … STRUCT}.
     * Members are matched by field name; ordering is ignored.
     */
    record StructDiff(
        DiffKind kind,
        List<MemberDiff<StructFieldDecl>> fields
    ) implements DeclDiff {}

    // ── Enum ──────────────────────────────────────────────────────────────────

    /**
     * Diff for a {@code CREATE TYPE … ENUM}.
     * {@code baseChanges} covers changes to the optional underlying primitive type.
     * Symbols are matched by name; ordering is ignored.
     */
    record EnumDiff(
        DiffKind kind,
        List<FieldChange> baseChanges,
        List<MemberDiff<EnumSymbolDecl>> symbols
    ) implements DeclDiff {}

    // ── Union ─────────────────────────────────────────────────────────────────

    /**
     * Diff for a {@code CREATE TYPE … UNION}.
     * Members are matched by name; ordering is ignored.
     */
    record UnionDiff(
        DiffKind kind,
        List<MemberDiff<UnionMemberDecl>> members
    ) implements DeclDiff {}

    // ── Scalar ────────────────────────────────────────────────────────────────

    /**
     * Diff for a {@code CREATE TYPE … SCALAR}.
     * {@code changes} covers the base {@link kafkasql.lang.syntax.ast.type.TypeNode}
     * and any fragment changes (DEFAULT, CHECK, DOC).
     */
    record ScalarDiff(
        DiffKind kind,
        List<FieldChange> changes
    ) implements DeclDiff {}

    // ── Derived type ──────────────────────────────────────────────────────────

    /**
     * Diff for a derived type declaration (e.g. {@code CREATE TYPE Alias AS LIST<Foo>}).
     */
    record DerivedDiff(
        DiffKind kind,
        List<FieldChange> changes
    ) implements DeclDiff {}

    // ── Stream ────────────────────────────────────────────────────────────────

    /**
     * Diff for a {@code CREATE STREAM}.
     * Members (message types) are matched by name; ordering is ignored.
     */
    record StreamDiff(
        DiffKind kind,
        List<MemberDiff<StreamMemberDecl>> members
    ) implements DeclDiff {}

    // ── Context ───────────────────────────────────────────────────────────────

    /**
     * Diff for a {@code CREATE CONTEXT}.
     * Content is fragment-only (the name matched earlier in statement matching).
     */
    record ContextDiff(
        DiffKind kind,
        List<FieldChange> changes
    ) implements DeclDiff {}

    // ── Kind change ───────────────────────────────────────────────────────────

    /**
     * Special case: the same-named declaration changed from one kind to another
     * (e.g. STRUCT → ENUM).  Always {@code MODIFIED}.  No member-level detail is
     * produced because the two declarations are structurally incomparable.
     */
    record KindChangeDiff(
        TypeKindDecl left,
        TypeKindDecl right
    ) implements DeclDiff {
        public DiffKind kind() { return DiffKind.MODIFIED; }
    }
}
