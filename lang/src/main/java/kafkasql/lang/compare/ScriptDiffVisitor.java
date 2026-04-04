package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.misc.Include;

/**
 * Visitor interface for traversing a {@link ScriptDiff} tree.
 *
 * <p>All methods have no-op defaults; implementors override only what they need.
 * Use {@link ScriptDiffWalker#walk(ScriptDiff, ScriptDiffVisitor)} to drive the
 * traversal.
 *
 * <h2>Call sequence</h2>
 * <pre>{@code
 *   onVersion?
 *   onInclude*
 *   for each statement:
 *     onBeginStatement
 *     onNote*                                   ← statement-level notes
 *     if CREATE MODIFIED with declDiff:
 *       onBeginDecl
 *         onKindChange                          ← if kind changed, no members follow
 *         OR
 *         onFieldChange*                        ← decl-level changes (scalar etc.)
 *         ( onBeginMember onFieldChange* onNote* onEndMember )*
 *       onEndDecl
 *     onEndStatement
 * }</pre>
 *
 * <p>The CLI ({@code DiffPrinter}) and LSP can both implement this interface
 * without duplicating the traversal logic.
 */
public interface ScriptDiffVisitor {

    /** Called when the version pragma differs between the two scripts. */
    default void onVersion(FieldChange change) {}

    /** Called for each include diff. */
    default void onInclude(MemberDiff<Include> inc) {}

    /**
     * Called when entering any statement diff, before any nested events.
     * The statement's {@link StmtDiff#kind()} and concrete type are available
     * for rendering decisions.
     */
    default void onBeginStatement(StmtDiff stmt) {}

    /** Called after all nested events for a statement diff. */
    default void onEndStatement(StmtDiff stmt) {}

    /**
     * Called when entering the {@link DeclDiff} of a MODIFIED CREATE statement.
     * Only fired for {@code kind == MODIFIED} with a non-null {@code declDiff}.
     */
    default void onBeginDecl(DeclDiff decl) {}

    /** Called after all events inside a decl diff. */
    default void onEndDecl(DeclDiff decl) {}

    /**
     * Called when the declaration kind changed (e.g. STRUCT → ENUM).
     * No member or field-change events follow within the current decl.
     */
    default void onKindChange(DeclDiff.KindChangeDiff kc) {}

    /**
     * Called when entering a member diff (struct field, enum symbol, union member,
     * or stream member).
     */
    default void onBeginMember(MemberDiff<?> member) {}

    /** Called after all events inside a member diff. */
    default void onEndMember(MemberDiff<?> member) {}

    /**
     * Called for each structural field change.
     * May be delivered at decl level (e.g. enum base-type, scalar base type)
     * or within a member (e.g. field type change).
     */
    default void onFieldChange(FieldChange change) {}

    /**
     * Called for each {@link SemanticNote}.
     * Delivered after the header of the enclosing statement or member;
     * the surrounding {@link #onBeginStatement} / {@link #onBeginMember} context
     * identifies the owning node.
     */
    default void onNote(SemanticNote note) {}
}
