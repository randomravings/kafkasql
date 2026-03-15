package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;

/**
 * Marks a struct field as dropped (soft-deprecated).
 * Dropped fields cannot be written to, but remain readable for backward compatibility.
 */
public record DroppedNode(Range range) implements DeclFragment {}
