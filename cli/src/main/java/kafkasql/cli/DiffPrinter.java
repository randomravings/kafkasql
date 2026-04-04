package kafkasql.cli;

import kafkasql.lang.compare.*;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.use.ContextUse;
import kafkasql.runtime.diagnostics.Range;

import java.io.PrintWriter;

/**
 * Renders a {@link ScriptDiff} as a human-readable, ANSI-coloured diff to a
 * {@link PrintWriter} (typically {@code System.out}).
 *
 * <p>Colour scheme:
 * <ul>
 *   <li>Red   — LEFT_ONLY (removed)</li>
 *   <li>Green — RIGHT_ONLY (added)</li>
 *   <li>Cyan  — MODIFIED</li>
 *   <li>Grey  — UNCHANGED (only printed when {@code showUnchanged = true})</li>
 * </ul>
 *
 * <p>Severity badges are shown when semantic enrichment has been applied.
 * Implements {@link ScriptDiffVisitor} — the traversal is driven by
 * {@link ScriptDiffWalker}.
 */
public final class DiffPrinter implements ScriptDiffVisitor {

    // ANSI escapes
    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREY   = "\u001B[90m";
    private static final String BOLD   = "\u001B[1m";

    private static final String MEMBER_INDENT = "    ";
    private static final String DETAIL_INDENT = "      ";

    private final PrintWriter out;
    private final boolean useColor;
    private final boolean showUnchanged;

    // Tracks indentation for onNote and onFieldChange based on current nesting depth
    private String noteIndent  = "";
    private String fieldIndent = "";

    public DiffPrinter(PrintWriter out, boolean useColor, boolean showUnchanged) {
        this.out           = out;
        this.useColor      = useColor;
        this.showUnchanged = showUnchanged;
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public void print(ScriptDiff diff) {
        ScriptDiffWalker.walk(diff, this, showUnchanged);
        out.flush();
    }

    // =========================================================================
    // ScriptDiffVisitor — script level
    // =========================================================================

    @Override
    public void onVersion(FieldChange change) {
        String left  = change.left()  != null ? "v" + change.left()  : "(none)";
        String right = change.right() != null ? "v" + change.right() : "(none)";
        line(CYAN, "~", "SET VERSION  " + left + "  →  " + right, null, null);
    }

    @Override
    public void onInclude(MemberDiff<Include> inc) {
        String path = inc.left() != null ? inc.left().path() : inc.right().path();
        switch (inc.kind()) {
            case LEFT_ONLY  -> line(RED,   "-", "INCLUDE '" + path + "'", rangeOf(inc.left()), null);
            case RIGHT_ONLY -> line(GREEN, "+", "INCLUDE '" + path + "'", null, rangeOf(inc.right()));
            case UNCHANGED  -> line(GREY,  " ", "INCLUDE '" + path + "'", null, null);
            case MODIFIED   -> line(CYAN,  "~", "INCLUDE '" + path + "'", null, null);
        }
    }

    // =========================================================================
    // ScriptDiffVisitor — statement level
    // =========================================================================

    @Override
    public void onBeginStatement(StmtDiff stmt) {
        if (stmt instanceof StmtDiff.CreateDiff cd) {
            renderCreateLine(cd);
        } else {
            renderSimpleLine(stmt);
        }
        noteIndent = MEMBER_INDENT;
    }

    @Override
    public void onEndStatement(StmtDiff stmt) {
        noteIndent = "";
    }

    // =========================================================================
    // ScriptDiffVisitor — decl level
    // =========================================================================

    @Override
    public void onBeginDecl(DeclDiff decl) {
        fieldIndent = MEMBER_INDENT;
    }

    @Override
    public void onEndDecl(DeclDiff decl) {
        fieldIndent = "";
    }

    @Override
    public void onKindChange(DeclDiff.KindChangeDiff kc) {
        out.println(MEMBER_INDENT + color(CYAN, "~ kind changed: "
            + kc.left().getClass().getSimpleName()
            + " → " + kc.right().getClass().getSimpleName()));
    }

    // =========================================================================
    // ScriptDiffVisitor — member level
    // =========================================================================

    @Override
    public void onBeginMember(MemberDiff<?> member) {
        renderMemberLine(member);
        fieldIndent = DETAIL_INDENT;
        noteIndent  = DETAIL_INDENT;
    }

    @Override
    public void onEndMember(MemberDiff<?> member) {
        fieldIndent = MEMBER_INDENT;
        noteIndent  = MEMBER_INDENT;
    }

    @Override
    public void onFieldChange(FieldChange change) {
        String lv = change.left()  != null ? nodeDesc(change.left())  : "(none)";
        String rv = change.right() != null ? nodeDesc(change.right()) : "(none)";
        out.println(fieldIndent + color(CYAN, "  " + change.aspect() + ": " + lv + " → " + rv));
    }

    @Override
    public void onNote(SemanticNote note) {
        String badge = switch (note.severity()) {
            case BREAKING -> color(RED,    "[BREAKING]");
            case WARNING  -> color(YELLOW, "[WARNING] ");
            case SAFE     -> color(GREEN,  "[SAFE]    ");
            case INFO     -> color(GREY,   "[INFO]    ");
        };
        out.println(noteIndent + badge + " " + note.message());
    }

    // =========================================================================
    // Private rendering helpers
    // =========================================================================

    private void renderCreateLine(StmtDiff.CreateDiff cd) {
        String name = cd.left() != null ? cd.left().decl().name().name()
                                        : cd.right().decl().name().name();
        String verb = declVerb(cd);
        switch (cd.kind()) {
            case LEFT_ONLY  -> line(RED,   "-", verb + " " + name, rangeOf(cd.left()),  null);
            case RIGHT_ONLY -> line(GREEN, "+", verb + " " + name, null, rangeOf(cd.right()));
            case UNCHANGED  -> line(GREY,  " ", verb + " " + name, null, null);
            case MODIFIED   -> line(CYAN,  "~", verb + " " + name, rangeOf(cd.left()), rangeOf(cd.right()));
        }
    }

    private void renderSimpleLine(StmtDiff stmt) {
        record Info(String verb, String target, Object left, Object right) {}
        Info info = switch (stmt) {
            case StmtDiff.DropDiff    dd -> new Info("DROP",    targetOf(dd), dd.left(), dd.right());
            case StmtDiff.AlterDiff   ad -> new Info("ALTER",   targetOf(ad), ad.left(), ad.right());
            case StmtDiff.UseDiff     ud -> new Info("USE",     targetOf(ud), ud.left(), ud.right());
            case StmtDiff.ReadDiff    rd -> new Info("READ",    targetOf(rd), rd.left(), rd.right());
            case StmtDiff.WriteDiff   wd -> new Info("WRITE",   targetOf(wd), wd.left(), wd.right());
            case StmtDiff.ShowDiff    sd -> new Info("SHOW",    null,          sd.left(), sd.right());
            case StmtDiff.ExplainDiff ed -> new Info("EXPLAIN", null,          ed.left(), ed.right());
            default -> throw new AssertionError("Unexpected stmt type in renderSimpleLine: " + stmt.getClass());
        };
        String label = info.verb() + (info.target() != null ? " " + info.target() : "");
        Range lr = info.left()  instanceof AstNode n ? n.range() : null;
        Range rr = info.right() instanceof AstNode n ? n.range() : null;
        switch (stmt.kind()) {
            case LEFT_ONLY  -> line(RED,   "-", label, lr,   null);
            case RIGHT_ONLY -> line(GREEN, "+", label, null, rr);
            case UNCHANGED  -> line(GREY,  " ", label, null, null);
            case MODIFIED   -> line(CYAN,  "~", label, lr,   rr);
        }
    }

    private void renderMemberLine(MemberDiff<?> member) {
        Object node  = member.left() != null ? member.left() : member.right();
        String mname = memberName(node);
        String typeAnnotation = (member.kind() != DiffKind.MODIFIED && node instanceof StructFieldDecl f)
            ? ": " + nodeDesc(f.type()) : "";
        boolean isStream = node instanceof StreamMemberDecl;
        String prefix = isStream ? "TYPE " : "";
        String display = prefix + mname + typeAnnotation;
        switch (member.kind()) {
            case LEFT_ONLY  -> out.println(MEMBER_INDENT + color(RED,   "- " + display));
            case RIGHT_ONLY -> out.println(MEMBER_INDENT + color(GREEN, "+ " + display));
            case UNCHANGED  -> out.println(MEMBER_INDENT + color(GREY,  "  " + display));
            case MODIFIED   -> out.println(MEMBER_INDENT + color(CYAN,  "~ " + (prefix + mname)));
        }
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    /** Print a single diff line with prefix, label, and optional location hints. */
    private void line(String ansi, String prefix, String label, Range lr, Range rr) {
        String loc = buildLoc(lr, rr);
        out.println(color(ansi, prefix + " " + label) + (loc.isEmpty() ? "" : color(GREY, "  " + loc)));
    }

    private String buildLoc(Range lr, Range rr) {
        if (lr == null && rr == null) return "";
        StringBuilder sb = new StringBuilder();
        if (lr != null && lr != Range.NONE) sb.append(formatRange(lr, "left"));
        if (rr != null && rr != Range.NONE) {
            if (!sb.isEmpty()) sb.append("  ");
            sb.append(formatRange(rr, "right"));
        }
        return sb.toString();
    }

    private String formatRange(Range r, String side) {
        if (r == null || r == Range.NONE) return "";
        String src = r.source().isEmpty() ? side : shortPath(r.source());
        return "[" + src + ":" + r.from().ln() + ":" + r.from().ch() + "]";
    }

    private String shortPath(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    private String color(String ansi, String text) {
        return useColor ? ansi + text + RESET : text;
    }

    private static Range rangeOf(AstNode node) {
        return node == null ? null : node.range();
    }

    private static String declVerb(StmtDiff.CreateDiff cd) {
        var decl = cd.left() != null ? cd.left().decl() : cd.right().decl();
        return switch (decl) {
            case TypeDecl    __ -> "CREATE TYPE";
            case StreamDecl  __ -> "CREATE STREAM";
            case ContextDecl __ -> "CREATE CONTEXT";
        };
    }

    private static String memberName(Object node) {
        return switch (node) {
            case StructFieldDecl  f -> f.name().name();
            case EnumSymbolDecl   s -> s.name().name();
            case UnionMemberDecl  m -> m.name().name();
            case StreamMemberDecl m -> m.memberDecl().name().name();
            default -> node.getClass().getSimpleName();
        };
    }

    private static String targetOf(StmtDiff.DropDiff dd) {
        var s = dd.left() != null ? dd.left() : dd.right();
        return s.target().fullName();
    }

    private static String targetOf(StmtDiff.AlterDiff ad) {
        var s = ad.left() != null ? ad.left() : ad.right();
        return s.target().fullName();
    }

    private static String targetOf(StmtDiff.UseDiff ud) {
        var s = ud.left() != null ? ud.left() : ud.right();
        return switch (s.target()) {
            case ContextUse cu -> "CONTEXT " + cu.qname().fullName();
        };
    }

    private static String targetOf(StmtDiff.ReadDiff rd) {
        var s = rd.left() != null ? rd.left() : rd.right();
        return s.stream().fullName();
    }

    private static String targetOf(StmtDiff.WriteDiff wd) {
        var s = wd.left() != null ? wd.left() : wd.right();
        return s.stream().fullName();
    }

    /** Best-effort short description of any AstNode for display. */
    private static String nodeDesc(AstNode node) {
        return switch (node) {
            case kafkasql.lang.syntax.ast.type.PrimitiveTypeNode p -> p.kind().name();
            case kafkasql.lang.syntax.ast.type.ComplexTypeNode c   -> c.name().fullName();
            case kafkasql.lang.syntax.ast.type.ListTypeNode l      -> "LIST<...>";
            case kafkasql.lang.syntax.ast.type.MapTypeNode m       -> "MAP<...>";
            case kafkasql.lang.syntax.ast.literal.NumberLiteralNode n -> n.text();
            case kafkasql.lang.syntax.ast.literal.StringLiteralNode s -> "'" + s.value() + "'";
            case kafkasql.lang.syntax.ast.literal.NullLiteralNode __ -> "NULL";
            case kafkasql.lang.syntax.ast.misc.VersionPragma vp  -> "v" + vp.version();
            default -> node.getClass().getSimpleName();
        };
    }
}
