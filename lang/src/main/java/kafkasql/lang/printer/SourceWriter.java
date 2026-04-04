package kafkasql.lang.printer;

import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.constExpr.*;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.fragment.*;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.type.*;
import kafkasql.runtime.type.PrimitiveKind;

/**
 * Serialises AST nodes back to valid KafkaSQL source text.
 *
 * <p>Unlike {@link AstPrinter} (which is a debug tree printer), this class produces
 * runnable KafkaSQL that can be re-parsed. It is used by
 * {@link kafkasql.lang.compare.DeltaScriptGenerator} to generate delta scripts.
 *
 * <p>Typical use:
 * <pre>{@code
 *   SourceWriter w = new SourceWriter();
 *   w.writeStructFieldDecl(field);
 *   String src = w.toString(); // "MyField STRING(64) NULL DEFAULT 'hello'"
 * }</pre>
 */
public final class SourceWriter {

    private final StringBuilder sb = new StringBuilder();

    public SourceWriter() {}

    /** Append raw text and return {@code this} for chaining. */
    public SourceWriter append(String s) {
        sb.append(s);
        return this;
    }

    /** Return the accumulated source text. */
    @Override
    public String toString() {
        return sb.toString();
    }

    // ─────────────────────── statement-level ───────────────────────────────────

    /** Writes {@code CREATE <decl>} (without trailing semicolon). */
    public void writeCreate(Decl decl) {
        sb.append("CREATE ");
        writeDecl(decl);
    }

    /**
     * Writes a drop statement, e.g. {@code DROP TYPE Foo} or
     * {@code DROP STREAM MyStream} (without trailing semicolon).
     */
    public void writeDrop(Decl decl) {
        switch (decl) {
            case TypeDecl    td -> sb.append("DROP TYPE ").append(td.name().name());
            case StreamDecl  sd -> sb.append("DROP STREAM ").append(sd.name().name());
            case ContextDecl cd -> sb.append("DROP CONTEXT ").append(cd.name().name());
        }
    }

    // ─────────────────────── declarations ──────────────────────────────────────

    public void writeDecl(Decl decl) {
        switch (decl) {
            case TypeDecl    td -> writeTypeDecl(td);
            case StreamDecl  sd -> writeStreamDecl(sd);
            case ContextDecl cd -> writeContextDecl(cd);
        }
    }

    public void writeTypeDecl(TypeDecl td) {
        sb.append("TYPE ").append(td.name().name()).append(" AS ");
        writeTypeKindDecl(td.kind());
        writeDeclFragments(td.fragments());
    }

    private void writeTypeKindDecl(TypeKindDecl kind) {
        switch (kind) {
            case ScalarDecl      s -> writeScalarDecl(s);
            case EnumDecl        e -> writeEnumDecl(e);
            case StructDecl      s -> writeStructDecl(s);
            case UnionDecl       u -> writeUnionDecl(u);
            case DerivedTypeDecl d -> writeDerivedTypeDecl(d);
        }
    }

    private void writeScalarDecl(ScalarDecl s) {
        sb.append("SCALAR ");
        writeTypeNode(s.type());
    }

    private void writeEnumDecl(EnumDecl e) {
        sb.append("ENUM");
        if (e.type().isPresent()) {
            sb.append(" ");
            writeTypeNode(e.type().get());
        }
        var symbols = e.symbols();
        sb.append(" (");
        for (int i = 0; i < symbols.size(); i++) {
            sb.append(i == 0 ? "\n    " : ",\n    ");
            writeEnumSymbolDecl(symbols.get(i));
        }
        sb.append("\n)");
    }

    private void writeStructDecl(StructDecl s) {
        var fields = s.fields();
        sb.append("STRUCT (");
        for (int i = 0; i < fields.size(); i++) {
            sb.append(i == 0 ? "\n    " : ",\n    ");
            writeStructFieldDecl(fields.get(i));
        }
        sb.append("\n)");
    }

    private void writeUnionDecl(UnionDecl u) {
        var members = u.members();
        sb.append("UNION (");
        for (int i = 0; i < members.size(); i++) {
            sb.append(i == 0 ? "\n    " : ",\n    ");
            writeUnionMemberDecl(members.get(i));
        }
        sb.append("\n)");
    }

    private void writeDerivedTypeDecl(DerivedTypeDecl d) {
        sb.append(d.target().name().fullName());
    }

    public void writeStreamDecl(StreamDecl sd) {
        sb.append("STREAM ").append(sd.name().name()).append(" (");
        var types = sd.streamTypes();
        for (int i = 0; i < types.size(); i++) {
            sb.append(i == 0 ? "\n    " : ",\n    ");
            writeStreamMemberDecl(types.get(i));
        }
        sb.append("\n)");
        writeDeclFragments(sd.fragments());
    }

    public void writeContextDecl(ContextDecl cd) {
        sb.append("CONTEXT ").append(cd.name().name());
        writeDeclFragments(cd.fragments());
    }

    // ─────────────────────── members ───────────────────────────────────────────

    /** Writes a struct field: {@code name type [NULL] [fragments]} */
    public void writeStructFieldDecl(StructFieldDecl f) {
        sb.append(f.name().name()).append(" ");
        writeTypeNode(f.type());
        if (f.nullable().isPresent()) sb.append(" NULL");
        writeDeclFragments(f.fragments());
    }

    /** Writes an enum symbol: {@code name = constExpr [fragments]} */
    public void writeEnumSymbolDecl(EnumSymbolDecl s) {
        sb.append(s.name().name()).append(" = ");
        writeConstExpr(s.value());
        writeDeclFragments(s.fragments());
    }

    /** Writes a union member: {@code name type [fragments]} */
    public void writeUnionMemberDecl(UnionMemberDecl m) {
        sb.append(m.name().name()).append(" ");
        writeTypeNode(m.type());
        writeDeclFragments(m.fragments());
    }

    /**
     * Writes a stream member (streamTypeDecl in the grammar):
     * {@code TYPE name AS kind [typeFragments] [streamFragments]}
     */
    public void writeStreamMemberDecl(StreamMemberDecl m) {
        writeTypeDecl(m.memberDecl());
        writeDeclFragments(m.fragments());
    }

    // ─────────────────────── type nodes ────────────────────────────────────────

    public void writeTypeNode(TypeNode t) {
        switch (t) {
            case PrimitiveTypeNode p -> writePrimitiveType(p);
            case ListTypeNode      l -> {
                sb.append("LIST<");
                writeTypeNode(l.elementType());
                sb.append(">");
            }
            case MapTypeNode m -> {
                sb.append("MAP<");
                writePrimitiveType(m.keyType());
                sb.append(", ");
                writeTypeNode(m.valueType());
                sb.append(">");
            }
            case ComplexTypeNode c -> sb.append(c.name().fullName());
        }
    }

    private void writePrimitiveType(PrimitiveTypeNode p) {
        sb.append(primitiveKeyword(p.kind()));
        switch (p.kind()) {
            case DECIMAL -> {
                if (p.hasPrecision()) {
                    sb.append("(").append(p.precision());
                    if (p.hasScale()) sb.append(", ").append(p.scale());
                    sb.append(")");
                }
            }
            case STRING, BYTES -> {
                if (p.hasLength()) sb.append("(").append(p.length()).append(")");
            }
            case TIME, TIMESTAMP, TIMESTAMP_TZ -> {
                if (p.hasPrecision()) sb.append("(").append(p.precision()).append(")");
            }
            default -> { /* no parameters */ }
        }
    }

    private static String primitiveKeyword(PrimitiveKind kind) {
        return switch (kind) {
            case BOOLEAN      -> "BOOLEAN";
            case INT8         -> "INT8";
            case INT16        -> "INT16";
            case INT32        -> "INT32";
            case INT64        -> "INT64";
            case FLOAT32      -> "FLOAT32";
            case FLOAT64      -> "FLOAT64";
            case DECIMAL      -> "DECIMAL";
            case STRING       -> "STRING";
            case BYTES        -> "BYTES";
            case UUID         -> "UUID";
            case DATE         -> "DATE";
            case TIME         -> "TIME";
            case TIMESTAMP    -> "TIMESTAMP";
            case TIMESTAMP_TZ -> "TIMESTAMP_TZ";
        };
    }

    // ─────────────────────── fragments ─────────────────────────────────────────

    public void writeDeclFragments(AstListNode<DeclFragment> fragments) {
        for (int i = 0; i < fragments.size(); i++) {
            sb.append(" ");
            writeDeclFragment(fragments.get(i));
        }
    }

    private void writeDeclFragment(DeclFragment f) {
        switch (f) {
            case DocNode d -> {
                sb.append("COMMENT '");
                sb.append(d.comment().replace("'", "''"));
                sb.append("'");
            }
            case DefaultNode d -> {
                sb.append("DEFAULT ");
                writeLiteralNode(d.value());
            }
            case CheckNode c -> {
                sb.append("CHECK(");
                writeExpr(c.expr());
                sb.append(")");
            }
            case ConstraintNode n -> {
                sb.append("CONSTRAINT ").append(n.name().name()).append(" (");
                writeDeclFragment(n.fragment());
                sb.append(")");
            }
            case DistributeDecl d -> {
                sb.append("DISTRIBUTE BY (");
                for (int i = 0; i < d.keys().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(d.keys().get(i).name());
                }
                sb.append(")");
            }
            case TimestampDecl t -> {
                sb.append("TIMESTAMP BY (").append(t.field().name()).append(")");
            }
            case DroppedNode __ -> sb.append("DROPPED");
        }
    }

    // ─────────────────────── expressions ───────────────────────────────────────

    public void writeExpr(Expr e) {
        switch (e) {
            case IdentifierExpr  id  -> sb.append(id.name().name());
            case LiteralExpr     lit -> writeLiteralNode(lit.literal());
            case ParenExpr       p   -> { sb.append("("); writeExpr(p.inner()); sb.append(")"); }
            case PrefixExpr      p   -> {
                switch (p.op()) {
                    case NOT -> sb.append("NOT ");
                    case NEG -> sb.append("-");
                }
                writeExpr(p.expr());
            }
            case PostfixExpr     p   -> {
                writeExpr(p.expr());
                switch (p.op()) {
                    case IS_NULL     -> sb.append(" IS NULL");
                    case IS_NOT_NULL -> sb.append(" IS NOT NULL");
                }
            }
            case InfixExpr       inf -> {
                writeExpr(inf.left());
                sb.append(" ").append(infixToken(inf.op())).append(" ");
                writeExpr(inf.right());
            }
            case TrifixExpr      t   -> {
                // only BETWEEN supported
                writeExpr(t.left());
                sb.append(" BETWEEN ");
                writeExpr(t.middle());
                sb.append(" AND ");
                writeExpr(t.right());
            }
            case MemberExpr      m   -> {
                writeExpr(m.target());
                sb.append(".").append(m.name().name());
            }
            case IndexExpr       ix  -> {
                writeExpr(ix.target());
                sb.append("[");
                writeExpr(ix.index());
                sb.append("]");
            }
        }
    }

    private static String infixToken(InfixOp op) {
        return switch (op) {
            case EQ     -> "=";
            case NEQ    -> "!=";
            case LT     -> "<";
            case LTE    -> "<=";
            case GT     -> ">";
            case GTE    -> ">=";
            case AND    -> "AND";
            case OR     -> "OR";
            case XOR    -> "XOR";
            case IN     -> "IN";
            case MUL    -> "*";
            case DIV    -> "/";
            case MOD    -> "%";
            case ADD    -> "+";
            case SUB    -> "-";
            case BITAND -> "&";
            case BITOR  -> "|";
            case SHL    -> "<<";
            case SHR    -> ">>";
            case CONCAT -> "||";
        };
    }

    // ─────────────────────── const expressions ─────────────────────────────────

    public void writeConstExpr(ConstExpr e) {
        switch (e) {
            case ConstLiteralExpr  lit -> sb.append(lit.text());
            case ConstSymbolRefExpr r  -> sb.append(r.name().name());
            case ConstParenExpr    p   -> { sb.append("("); writeConstExpr(p.inner()); sb.append(")"); }
            case ConstBinaryExpr   b   -> {
                writeConstExpr(b.left());
                sb.append(" ").append(constBinaryToken(b.op())).append(" ");
                writeConstExpr(b.right());
            }
        }
    }

    private static String constBinaryToken(ConstBinaryOp op) {
        return switch (op) {
            case ADD    -> "+";
            case SUB    -> "-";
            case MUL    -> "*";
            case DIV    -> "/";
            case MOD    -> "%";
            case SHL    -> "<<";
            case SHR    -> ">>";
            case BITAND -> "&";
            case BITOR  -> "|";
            case BITXOR -> "^";
        };
    }

    // ─────────────────────── literals ──────────────────────────────────────────

    public void writeLiteralNode(LiteralNode lit) {
        switch (lit) {
            case NullLiteralNode   __  -> sb.append("NULL");
            case BoolLiteralNode   b   -> sb.append(Boolean.TRUE.equals(b.value()) ? "TRUE" : "FALSE");
            case NumberLiteralNode n   -> sb.append(n.text());
            case StringLiteralNode s   -> {
                sb.append("'");
                sb.append(s.value().replace("'", "''"));
                sb.append("'");
            }
            case BytesLiteralNode  b   -> sb.append(b.text());
            case ListLiteralNode   l   -> {
                sb.append("[");
                for (int i = 0; i < l.elements().size(); i++) {
                    if (i > 0) sb.append(", ");
                    writeLiteralNode(l.elements().get(i));
                }
                sb.append("]");
            }
            case MapLiteralNode    m   -> {
                sb.append("{");
                for (int i = 0; i < m.entries().size(); i++) {
                    if (i > 0) sb.append(", ");
                    var entry = m.entries().get(i);
                    writeLiteralNode(entry.key());
                    sb.append(": ");
                    writeLiteralNode(entry.value());
                }
                sb.append("}");
            }
            case StructLiteralNode s   -> {
                sb.append("{");
                for (int i = 0; i < s.fields().size(); i++) {
                    if (i > 0) sb.append(", ");
                    var f = s.fields().get(i);
                    sb.append(f.name().name()).append(": ");
                    writeLiteralNode(f.value());
                }
                sb.append("}");
            }
            case EnumLiteralNode   e   -> {
                sb.append(e.enumName().fullName()).append("::").append(e.symbol().name());
            }
            case UnionLiteralNode  u   -> {
                sb.append(u.unionName().fullName()).append("$").append(u.memberName().name());
                sb.append("(");
                writeLiteralNode(u.value());
                sb.append(")");
            }
        }
    }
}
