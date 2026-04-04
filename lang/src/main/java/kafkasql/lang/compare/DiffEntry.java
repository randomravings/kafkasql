package kafkasql.lang.compare;

import kafkasql.runtime.diagnostics.Range;

/**
 * A flat, LSP-ready diff entry produced by {@link ScriptDiff#flatten()}.
 *
 * <p>Consumers (e.g. the language server) can map:
 * <ul>
 *   <li>{@link DiffSeverity#BREAKING} → {@code DiagnosticSeverity.Error}</li>
 *   <li>{@link DiffSeverity#WARNING}  → {@code DiagnosticSeverity.Warning}</li>
 *   <li>{@link DiffSeverity#SAFE}     → {@code DiagnosticSeverity.Information}</li>
 *   <li>{@link DiffSeverity#INFO}     → {@code DiagnosticSeverity.Hint}</li>
 * </ul>
 *
 * @param kind       Whether the element was added, removed, or changed.
 * @param leftRange  Source location in the left (old) document — {@code null} when
 *                   {@code kind == RIGHT_ONLY}.
 * @param rightRange Source location in the right (new) document — {@code null} when
 *                   {@code kind == LEFT_ONLY}.
 * @param severity   Semantic severity; {@link DiffSeverity#INFO} when no enrichment
 *                   has been applied yet.
 * @param aspect     Which structural aspect this entry describes.
 * @param message    Human-readable description for display in the IDE.
 */
public record DiffEntry(
    DiffKind kind,
    Range leftRange,
    Range rightRange,
    DiffSeverity severity,
    String aspect,
    String message
) {}
