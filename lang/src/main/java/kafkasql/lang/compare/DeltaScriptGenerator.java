package kafkasql.lang.compare;

import kafkasql.lang.printer.SourceWriter;
import kafkasql.lang.syntax.ast.decl.*;

/**
 * Generates a minimal KafkaSQL delta script from a {@link ScriptDiff}.
 *
 * <p>The output contains only the statements needed to evolve the left schema to the
 * right schema, based on the diff of {@code CREATE} declarations:
 * <ul>
 *   <li><b>Added</b> ({@code RIGHT_ONLY}): {@code CREATE ...;}</li>
 *   <li><b>Removed</b> ({@code LEFT_ONLY}): {@code DROP TYPE|STREAM|CONTEXT ...;}</li>
 *   <li><b>Modified (struct)</b>: {@code ALTER TYPE name ADD field;} for new fields,
 *       {@code ALTER TYPE name DROP field;} for removed or soft-dropped fields.
 *       In-place changes to type, nullability, or other metadata cannot be expressed
 *       as migration statements (the underlying Kafka log is immutable) and are omitted.</li>
 *   <li><b>Modified (enum, no base change)</b>: {@code ALTER TYPE name ADD symbol;} for new symbols,
 *       {@code ALTER TYPE name DROP symbol;} for removed symbols.
 *       In-place symbol value changes cannot be expressed and are omitted.</li>
 *   <li><b>Modified (stream)</b>: {@code ALTER STREAM name ADD TYPE member;} for new message types,
 *       {@code ALTER STREAM name DROP TYPE member;} for removed message types.
 *       In-place changes to a stream member's inner struct cannot be expressed and are omitted.</li>
 *   <li><b>Modified (union / scalar / derived / context / kind change)</b>:
 *       {@code DROP ...; CREATE ...;} (no granular ALTER syntax exists)</li>
 * </ul>
 *
 * <p>Only {@link StmtDiff.CreateDiff} entries are processed.  All other statement
 * types (ALTER, DROP, USE, READ, WRITE, SHOW, EXPLAIN) are ignored.
 */
public final class DeltaScriptGenerator {

    private DeltaScriptGenerator() {}

    /**
     * Generate a delta script from the given diff.
     *
     * @param diff the script diff to process
     * @return valid KafkaSQL source text representing the minimal set of
     *         statements to evolve the left schema to the right schema,
     *         or an empty string if there is nothing to generate
     */
    public static String generate(ScriptDiff diff) {
        StringBuilder sb = new StringBuilder();
        for (StmtDiff stmt : diff.statements()) {
            if (!(stmt instanceof StmtDiff.CreateDiff cd)) continue;
            switch (cd.kind()) {
                case RIGHT_ONLY -> appendCreate(sb, cd.right().decl());
                case LEFT_ONLY  -> appendDrop(sb, cd.left().decl());
                case MODIFIED   -> appendModified(sb, cd);
                case UNCHANGED  -> { /* nothing */ }
            }
        }
        return sb.toString().trim();
    }

    // ─── create ────────────────────────────────────────────────────────────────

    private static void appendCreate(StringBuilder sb, Decl decl) {
        SourceWriter w = new SourceWriter();
        w.writeCreate(decl);
        sb.append(w).append(";\n\n");
    }

    // ─── drop ──────────────────────────────────────────────────────────────────

    private static void appendDrop(StringBuilder sb, Decl decl) {
        SourceWriter w = new SourceWriter();
        w.writeDrop(decl);
        sb.append(w).append(";\n\n");
    }

    // ─── modified top-level ────────────────────────────────────────────────────

    private static void appendModified(StringBuilder sb, StmtDiff.CreateDiff cd) {
        if (cd.declDiff() == null) return;
        switch (cd.declDiff()) {
            case DeclDiff.StructDiff   sd -> appendStructAlters(sb, cd.left().decl().name().name(), sd);
            case DeclDiff.EnumDiff     ed -> appendEnumAlters(sb, cd, ed);
            case DeclDiff.StreamDiff   sd -> appendStreamAlters(sb, cd.left().decl().name().name(), sd);
            // No ALTER syntax for these — must DROP then CREATE
            case DeclDiff.UnionDiff    __ -> appendDropCreate(sb, cd);
            case DeclDiff.ScalarDiff   __ -> appendDropCreate(sb, cd);
            case DeclDiff.DerivedDiff  __ -> appendDropCreate(sb, cd);
            case DeclDiff.ContextDiff  __ -> appendDropCreate(sb, cd);
            case DeclDiff.KindChangeDiff __ -> appendDropCreate(sb, cd);
        }
    }

    /** Drop left, create right — used when no granular ALTER syntax exists. */
    private static void appendDropCreate(StringBuilder sb, StmtDiff.CreateDiff cd) {
        appendDrop(sb, cd.left().decl());
        appendCreate(sb, cd.right().decl());
    }

    // ─── struct ALTERs ─────────────────────────────────────────────────────────

    private static void appendStructAlters(StringBuilder sb, String typeName, DeclDiff.StructDiff sd) {
        for (var md : sd.fields()) {
            switch (md.kind()) {
                case RIGHT_ONLY -> {
                    SourceWriter w = new SourceWriter();
                    w.append("ALTER TYPE ").append(typeName).append(" ADD ");
                    w.writeStructFieldDecl(md.right());
                    sb.append(w).append(";\n\n");
                }
                case LEFT_ONLY -> {
                    sb.append("ALTER TYPE ").append(typeName)
                      .append(" DROP ").append(md.left().name().name()).append(";\n\n");
                }
                case MODIFIED -> {
                    // Only a newly-applied soft-drop can be expressed: the in-place type or
                    // nullability change and metadata changes (default, check, doc) have no
                    // ALTER syntax — the Kafka log is immutable, so emit nothing for those.
                    boolean isSoftDrop = md.changes().stream()
                        .anyMatch(c -> "dropped".equals(c.aspect()) && c.left() == null);
                    if (isSoftDrop) {
                        sb.append("ALTER TYPE ").append(typeName)
                          .append(" DROP ").append(md.left().name().name()).append(";\n\n");
                    }
                }
                case UNCHANGED -> { /* nothing */ }
            }
        }
    }

    // ─── enum ALTERs ───────────────────────────────────────────────────────────

    private static void appendEnumAlters(StringBuilder sb, StmtDiff.CreateDiff cd, DeclDiff.EnumDiff ed) {
        if (!ed.baseChanges().isEmpty()) {
            // Enum base type changed — ALTER cannot change the base type; DROP + CREATE
            appendDropCreate(sb, cd);
            return;
        }
        String typeName = cd.left().decl().name().name();
        for (var md : ed.symbols()) {
            switch (md.kind()) {
                case RIGHT_ONLY -> {
                    SourceWriter w = new SourceWriter();
                    w.append("ALTER TYPE ").append(typeName).append(" ADD ");
                    w.writeEnumSymbolDecl(md.right());
                    sb.append(w).append(";\n\n");
                }
                case LEFT_ONLY -> {
                    sb.append("ALTER TYPE ").append(typeName)
                      .append(" DROP ").append(md.left().name().name()).append(";\n\n");
                }
                case MODIFIED -> {
                    // Enum symbol value changes cannot be expressed as ALTER — the integer
                    // discriminant is on disk and mutable only by DROP + ADD a new symbol.
                    // Doc-only changes also have no ALTER syntax. Omit from delta.
                }
                case UNCHANGED -> { /* nothing */ }
            }
        }
    }

    // ─── stream ALTERs ─────────────────────────────────────────────────────────

    private static void appendStreamAlters(StringBuilder sb, String streamName, DeclDiff.StreamDiff sd) {
        for (var md : sd.members()) {
            switch (md.kind()) {
                case RIGHT_ONLY -> {
                    SourceWriter w = new SourceWriter();
                    w.append("ALTER STREAM ").append(streamName).append(" ADD ");
                    w.writeStreamMemberDecl(md.right());
                    sb.append(w).append(";\n\n");
                }
                case LEFT_ONLY -> {
                    sb.append("ALTER STREAM ").append(streamName)
                      .append(" DROP TYPE ").append(md.left().name().name()).append(";\n\n");
                }
                case MODIFIED -> {
                    // No ALTER STREAM ... MODIFY TYPE syntax exists. Changes to a stream
                    // member's inner struct (type changes, field modifications) cannot be
                    // expressed — the log is immutable. Omit from delta.
                }
                case UNCHANGED -> { /* nothing */ }
            }
        }
    }
}
