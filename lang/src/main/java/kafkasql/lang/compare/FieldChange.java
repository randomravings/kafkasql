package kafkasql.lang.compare;

import kafkasql.lang.syntax.ast.AstNode;

/**
 * A single structural aspect that changed between the left and right versions.
 *
 * <p>Both {@code left} and {@code right} are full AST nodes so their
 * {@link AstNode#range()} can be used to locate the change in the source text.
 *
 * @param aspect  Name of the structural aspect that changed (e.g. "type", "nullable",
 *                "default", "check", "dropped", "doc", "distribute", "timestamp",
 *                or "constraint:&lt;name&gt;" for named constraints).
 * @param left    The AST node from the left script, or {@code null} when the aspect
 *                was absent on the left (i.e. newly added on the right).
 * @param right   The AST node from the right script, or {@code null} when the aspect
 *                was absent on the right (i.e. removed from the left).
 */
public record FieldChange(
    String aspect,
    AstNode left,
    AstNode right
) {}
