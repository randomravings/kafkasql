package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.AstNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Diff result for a single named member (struct field, enum symbol, union member,
 * or stream type).
 *
 * <p>Both {@code left} and {@code right} carry the original AST nodes, so their
 * {@link AstNode#range()} can be used to locate the member in the source text —
 * which is the key requirement for LSP integration.
 *
 * <p>{@code notes} starts empty and is populated by {@link SemanticEnricher}
 * in an optional second pass.
 *
 * @param <T> The concrete member declaration type.
 */
public record MemberDiff<T extends AstNode>(
    DiffKind kind,
    T left,                      // null when RIGHT_ONLY
    T right,                     // null when LEFT_ONLY
    List<FieldChange> changes,   // structural aspects that differ (empty unless MODIFIED)
    List<SemanticNote> notes     // enriched annotations, populated after construction
) {

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    public static <T extends AstNode> MemberDiff<T> leftOnly(T left) {
        return new MemberDiff<>(DiffKind.LEFT_ONLY, left, null, List.of(), new ArrayList<>());
    }

    public static <T extends AstNode> MemberDiff<T> rightOnly(T right) {
        return new MemberDiff<>(DiffKind.RIGHT_ONLY, null, right, List.of(), new ArrayList<>());
    }

    public static <T extends AstNode> MemberDiff<T> unchanged(T left, T right) {
        return new MemberDiff<>(DiffKind.UNCHANGED, left, right, List.of(), new ArrayList<>());
    }

    public static <T extends AstNode> MemberDiff<T> modified(T left, T right, List<FieldChange> changes) {
        return new MemberDiff<>(DiffKind.MODIFIED, left, right, List.copyOf(changes), new ArrayList<>());
    }
}
