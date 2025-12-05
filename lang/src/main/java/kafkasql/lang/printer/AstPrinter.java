package kafkasql.lang.printer;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import kafkasql.lang.syntax.ast.*;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.fragment.CheckNode;
import kafkasql.lang.syntax.ast.fragment.ConstraintNode;
import kafkasql.lang.syntax.ast.fragment.DeclFragment;
import kafkasql.lang.syntax.ast.fragment.DefaultNode;
import kafkasql.lang.syntax.ast.fragment.DistributeDecl;
import kafkasql.lang.syntax.ast.fragment.DocNode;
import kafkasql.lang.syntax.ast.fragment.ProjectionExprNode;
import kafkasql.lang.syntax.ast.fragment.ProjectionNode;
import kafkasql.lang.syntax.ast.fragment.TimestampDecl;
import kafkasql.lang.syntax.ast.fragment.WhereNode;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.misc.*;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.type.*;
import kafkasql.lang.syntax.ast.use.ContextUse;
import kafkasql.lang.syntax.ast.use.UseTarget;

public final class AstPrinter extends Printer {

    private static final String PIPE  = "│  ";
    private static final String MID   = "├─ ";
    private static final String LAST  = "└─ ";
    private static final String EMPTY = "   ";
    
    private final ArrayList<Boolean> pipes = new ArrayList<>();

    public AstPrinter(Writer out) {
        super(out);
    }

    //============================================================
    // ENTRY
    //============================================================
    public void write(Script script) throws IOException {
        writeClass(script.getClass());
        branch("source", 0, false);
        write(script.range().source());
        branch("includes", 0, false);
        forEach(script.includes(), "include", this::writeInclude, 1, Include.class);
        branch("stmts", 0, true);
        forEach(script.statements(), "stmt", this::writeStmt, 1, Stmt.class);
        newLine();
    }

    //============================================================
    // CORE TREE MECHANICS
    //============================================================
    private void branch(String name,  int indent, boolean last) throws IOException {
        while (indent >= pipes.size())
            pipes.add(false);
        pipes.set(indent, !last);
        
        newLine();
        pipes(indent);

        write(last ? LAST : MID);
        write(name);
        write(": ");
    }

    private void pipes(int indent) throws IOException {
        for (int i = 0; i < indent; i++)
            write(pipes.get(i) ? PIPE : EMPTY);
    }

    private void writeInclude(Include inc, int indent) throws IOException {
        writeClass(inc.getClass());
        branch("path", indent, true);
        write(inc.path());
    }

    //============================================================
    // STATEMENTS
    //============================================================
    private void writeStmt(Stmt stmt, int indent) throws IOException {
        switch (stmt) {
            case UseStmt u      -> writeUseStmt(u, indent);
            case CreateStmt c   -> writeCreateStmt(c, indent);
            case ReadStmt r     -> writeRead(r, indent);
            case WriteStmt w    -> writeWrite(w, indent);
        }
    }

    //============================================================
    // TOP LEVEL STATEMENTS
    //============================================================

    private void writeUseStmt(UseStmt u, int indent) throws IOException {
        writeClass(u.getClass());
        branch("target", indent, true);
        writeUseTarget(u.target(), indent + 1);
    }

    private void writeUseTarget(UseTarget t, int indent) throws IOException {
        switch (t) {
            case ContextUse cu -> writeUseContext(cu, indent);
        }
    }

    private void writeCreateStmt(CreateStmt c, int indent) throws IOException {
        writeClass(c.getClass());
        branch("decl", indent, true);
        writeDecl(c.decl(), indent + 1);
    }

    private void writeDecl(Decl d, int indent) throws IOException {
        switch (d) {
            case TypeDecl t   -> writeTypeDecl(t, indent);
            case StreamDecl s -> writeStreamDecl(s, indent);
            case ContextDecl c -> writeCreateContext(c, indent);
        }
    }

    //============================================================
    // DECLARATION FRAGMENTS
    //============================================================

    private void writeDeclaratonFragmentList(AstListNode<DeclFragment> fragments, int indent) throws IOException {
        branch("fragments", indent, true);
        forEach(fragments, "fragment", this::writeDeclaratonFragment, indent + 1, DeclFragment.class);
    }

    private void writeDeclaratonFragment(DeclFragment fragment, int indent) throws IOException {
        switch (fragment) {
            case DefaultNode def ->
                writeDefaultFragment(def, indent);
            case DocNode doc ->
                writeCommentFragment(doc, indent);
            case CheckNode check ->
                writeCheckFragment(check, indent);
            case ConstraintNode nc ->
                writeNamedConstraintFragment(nc, indent);
            case DistributeDecl dist ->
                writeDistributeFragment(dist, indent);
            case TimestampDecl ts ->
                writeTimestampFragment(ts, indent);
        }
    }

    private void writeNamedConstraintFragment(ConstraintNode c, int indent) throws IOException {
        writeClass(c.getClass());
        branch("name", indent, false);
        write(c.name().name());
        branch("constraint", indent, true);
        writeDeclaratonFragment(c.fragment(), indent + 1);
    }

    private void writeCheckFragment(CheckNode c, int indent) throws IOException {
        writeClass(c.getClass());
        branch("expr", indent, true);
        writeExpr(c.expr(), indent + 1);
    }

    private void writeDefaultFragment(DefaultNode d, int indent) throws IOException {
        branch("default", indent, true);
        writeLiteral(d.value(), indent);
    }

    private void writeCommentFragment(DocNode c, int indent) throws IOException {
        branch("comment", indent, true);
        writeClass(DocNode.class);
        branch("lines", indent + 1, true);
        var padding = "lines: ".length();
        var lines = c.comment().lines().toList();
        write(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            newLine();
            pipes(indent + 2);
            spaces(padding);
            write(lines.get(i));
        }
    }

    private void writeDistributeFragment(DistributeDecl d, int indent) throws IOException {
        writeClass(d.getClass());
        branch("keys", indent, true);
        forEach(d.keys(), "key", this::writeIdentifier, indent + 1, Identifier.class);
    }

    private void writeTimestampFragment(TimestampDecl t, int indent) throws IOException {
        writeClass(t.getClass());
        branch("field", indent, false);
        writeIdentifier(t.field(), indent);
    }

    //============================================================
    // USE CONTEXT
    //============================================================

    private void writeUseContext(ContextUse u, int indent) throws IOException {
        writeClass(u.getClass());
        branch("context", indent, true);
        writeQName(u.qname(), indent + 1);
    }

    //============================================================
    // CONTEXT DECLARATION
    //============================================================
    private void writeCreateContext(ContextDecl c, int indent) throws IOException {
        writeClass(c.getClass());
        writeName(c.name(), indent, false);
        writeDeclaratonFragmentList(c.fragments(), indent);
    }

    //============================================================
    // TYPE DECLARATIONS
    //============================================================

    private void writeTypeDecl(TypeDecl d, int indent) throws IOException {
        write(d.getClass().getSimpleName());
        branch("name", indent, false);
        writeIdentifier(d.name(), indent + 1);
        branch("kind", indent, true);
        writeTypeKindDecl(d.kind(), indent + 1);
        writeDeclaratonFragmentList(d.fragments(), indent);
    }

    private void writeTypeKindDecl(TypeKindDecl k, int indent) throws IOException {
        switch (k) {
            case ScalarDecl s -> writeScalarDecl(s, indent);
            case EnumDecl e   -> writeEnumDecl(e, indent);
            case StructDecl s -> writeStructDecl(s, indent);
            case UnionDecl u  -> writeUnionDecl(u, indent);
            case DerivedTypeDecl d -> writeDerivedTypeDecl(d, indent);
        }
    }

    //============================================================
    // SCALAR
    //============================================================
    private void writeScalarDecl(ScalarDecl s, int indent) throws IOException {
        writeClass(s.getClass());
        branch("type", indent, false);
        writeTypeNode(s.type(), indent + 1, true);
    }

    //============================================================
    // ENUM
    //============================================================
    private void writeEnumDecl(EnumDecl e, int indent) throws IOException {
        writeClass(e.getClass());
        writeEnumSymbols(e.symbols(), indent);
    }

    private void writeEnumSymbols(AstListNode<EnumSymbolDecl> symbols, int indent) throws IOException {
        branch("symbols", indent, false);
        forEach(symbols, "symbol", this::writeEnumSymbolDecl, indent + 1, EnumSymbolDecl.class);
    }

    private void writeEnumSymbolDecl(EnumSymbolDecl s, int indent) throws IOException {
        writeClass(s.getClass());
        writeName(s.name(), indent, false);
    }

    //============================================================
    // STRUCT DECL
    //============================================================
    private void writeStructDecl(StructDecl s, int indent) throws IOException {
        writeClass(s.getClass());
        writeFields(s.fields(), indent, false);
    }

    private void writeFields(AstListNode<StructFieldDecl> fields, int indent, boolean last) throws IOException {
        branch("fields", indent, last);
        forEach(fields, "field", this::writeStructFieldDecl, indent + 1, StructFieldDecl.class);
    }

    private void writeStructFieldDecl(StructFieldDecl f, int indent) throws IOException {
        writeClass(f.getClass());
        writeName(f.name(), indent, false);
        writeNullable(f.nullable(), indent, false);
        writeTypeNode(f.type(), indent, false);
        writeDeclaratonFragmentList(f.fragments(), indent);
    }

    private void writeNullable(AstOptionalNode<NullLiteralNode> nullable, int indent, boolean last) throws IOException {
        branch("nullable", indent, last);
        writeOptional(nullable, this::writeLiteral, indent);
    }

    //============================================================
    // UNION DECL
    //============================================================
    private void writeUnionDecl(UnionDecl u, int indent) throws IOException {
        writeClass(u.getClass());
        writeUnionMemberDecls(u.members(), indent, false);
    }

    private void writeUnionMemberDecls(AstListNode<UnionMemberDecl> members, int indent, boolean last) throws IOException {
        branch("members", indent, last);
        forEach(members, "member", this::writeUnionMemberDecl, indent + 1, UnionMemberDecl.class);
    }

    private void writeUnionMemberDecl(UnionMemberDecl m, int indent) throws IOException {
        writeClass(m.getClass());
        writeName(m.name(), indent, false);
        writeTypeNode(m.type(), indent, false);
        writeDeclaratonFragmentList(m.fragments(), indent);
    }

    //============================================================
    // DERIVED TYPE DECL
    //============================================================

    private void writeDerivedTypeDecl(DerivedTypeDecl d, int indent) throws IOException {
        writeClass(d.getClass());
        branch("baseType", indent, true);
        writeQName(d.target().name(), indent + 1);
    }

    //============================================================
    // TYPE NODE
    //============================================================
    private void writeTypeNode(TypeNode t, int indent, boolean last) throws IOException {
        branch("type", indent, last);
        switch (t) {
            case PrimitiveTypeNode p -> writePrimitiveTypeNode(p, indent + 1);
            case CompositeTypeNode c -> writeCompositeTypeNode(c, indent + 1);
            case ComplexTypeNode r -> writeComplexTypeNode(r, indent + 1);
        }
    }

    private void writePrimitiveTypeNode(PrimitiveTypeNode p, int indent) throws IOException {
        writeClass(p.getClass());
        branch("type", indent, false);
        write(p.kind().toString());
        branch("length", indent, false);
        write(p.length());
        branch("precision", indent, false);
        write(p.precision());
        branch("scale", indent, true);
        write(p.scale());
    }

    private void writeCompositeTypeNode(CompositeTypeNode c, int indent) throws IOException {
        switch (c) {
            case ListTypeNode l -> writeListTypeNode(l, indent);
            case MapTypeNode m -> writeMapTypeNode(m, indent);
        }
    }

    private void writeListTypeNode(ListTypeNode l, int indent) throws IOException {
        writeClass(l.getClass());
        branch("elementType", indent, true);
        writeTypeNode(l.elementType(), indent + 1, true);
    }

    private void writeMapTypeNode(MapTypeNode m, int indent) throws IOException {
        writeClass(m.getClass());
        branch("keyType", indent, false);
        writeTypeNode(m.keyType(), indent + 1, true);
        branch("valueType", indent, true);
        writeTypeNode(m.valueType(), indent + 1, true);
    }

    private void writeComplexTypeNode(ComplexTypeNode r, int indent) throws IOException {
        writeClass(r.getClass());
        branch("ref", indent, true);
        writeQName(r.name(), indent + 1);
    }

    //============================================================
    // CREATE STREAM
    //============================================================
    
    private void writeStreamDecl(StreamDecl s, int indent) throws IOException {
        writeClass(s.getClass());
        branch("fqn", indent, false);
        writeIdentifier(s.name(), indent + 1);
        branch("streamTypes", indent, true);
        forEach(s.streamTypes(), "streamType", this::writeStreamType, indent + 1, StreamMemberDecl.class);
    }

    private void writeStreamType(StreamMemberDecl m, int indent) throws IOException {
        writeClass(m.getClass());
        branch("name", indent, false);
        writeIdentifier(m.name(), indent);
        branch("type", indent, true);
        writeTypeDecl(m.memberDecl(), indent);
    }

    //============================================================
    // WRITE
    //============================================================
    private void writeWrite(WriteStmt w, int indent) throws IOException {
        writeClass(w.getClass());
        branch("fqn", indent, false);
        writeQName(w.stream(), indent + 1);
        branch("type", indent, false);
        write(w.alias().name());
        branch("values", indent, true);
        forEach(w.values(), "value", this::writeStructLiteral, indent + 1, StructLiteralNode.class);
    }

    //============================================================
    // READ
    //============================================================
    private void writeRead(ReadStmt r, int indent) throws IOException {
        writeClass(r.getClass());
        branch("fqn", indent, false);
        writeQName(r.stream(), indent + 1);
        branch("blocks", indent, true);
        forEach(r.blocks(), "block", this::writeReadBlock, indent + 1, ReadTypeBlock.class);
    }

    private void writeReadBlock(ReadTypeBlock b, int indent) throws IOException {
        writeClass(b.getClass());
        branch("projection", indent, false);
        writeProjection(b.projection(), indent);
        branch("filter", indent, true);
        writeOptional(b.where(), this::writeWhere, indent);
    }

    private void writeProjection(ProjectionNode p, int indent) throws IOException {
        if (p.items().isEmpty())
            write("*");
        else
            forEach(p.items(), "expr", this::writeProjectionExpr, indent + 1, ProjectionExprNode.class);
    }

    private void writeProjectionExpr(ProjectionExprNode p, int indent) throws IOException {
        writeClass(p.getClass());
        branch("expr", indent, false);
        writeExpr(p.expr(), indent + 1);
        branch("alias", indent, true);
        writeOptional(p.alias(), this::writeIdentifier, indent);
    }

    private void writeWhere(WhereNode w, int indent) throws IOException {
        writeClass(w.getClass());
        branch("expr", indent, true);
        writeExpr(w.expr(), indent + 1);
    }

    //============================================================
    // EXPRESSIONS
    //============================================================
    private void writeExpr(Expr e, int indent) throws IOException {
        switch (e) {
            case IdentifierExpr id    -> writeIdentifierExpr(id, indent);
            case LiteralExpr lit      -> writeLiteralExpr(lit, indent);
            case InfixExpr inf        -> writeInfixExpr(inf, indent);
            case PrefixExpr pre       -> writePrefixExpr(pre, indent);
            case PostfixExpr post     -> writePostfixExpr(post, indent);
            case MemberExpr mem       -> writeMemberExpr(mem, indent);
            case IndexExpr idx        -> writeIndexExpr(idx, indent);
            case TrifixExpr t         -> writeTrifixExpr(t, indent);
            case ParenExpr p          -> writeParenExpr(p, indent);
        }
    }

    private void writeIdentifierExpr(IdentifierExpr id, int indent) throws IOException {
        writeClass(id.getClass());
        branch("id", indent, true);
        writeIdentifier(id.name(), indent + 1);
    }

    private void writeLiteralExpr(LiteralExpr lit, int indent) throws IOException {
        writeClass(lit.getClass());
        branch("literal", indent, true);
        writeLiteral(lit.literal(), indent + 1);
    }

    private void writeParenExpr(ParenExpr p, int indent) throws IOException {
        writeClass(p.getClass());
        branch("inner", indent, false);
        writeExpr(p.inner(), indent + 1);
    }

    private void writePrefixExpr(PrefixExpr p, int indent) throws IOException {
        writeClass(p.getClass());
        branch("op", indent, false);
        write(p.op().toString());
        branch("expr", indent, true);
        writeExpr(p.expr(), indent + 1);
    }

    private void writePostfixExpr(PostfixExpr p, int indent) throws IOException {
        writeClass(p.getClass());
        branch("op", indent, true);
        write(p.op().toString());
        branch("expr", indent, false);
        writeExpr(p.expr(), indent + 1);
    }

    private void writeInfixExpr(InfixExpr inf, int indent) throws IOException {
        writeClass(inf.getClass());
        branch("op", indent, false);
        write(inf.op().toString());
        branch("left", indent, false);
        writeExpr(inf.left(), indent + 1);
        branch("right", indent, true);
        writeExpr(inf.right(), indent + 1);
    }

    private void writeMemberExpr(MemberExpr m, int indent) throws IOException {
        writeClass(m.getClass());
        branch("target", indent, false);
        writeExpr(m.target(), indent + 1);
        branch("member", indent, true);
        writeIdentifier(m.name(), indent);
    }

    private void writeIndexExpr(IndexExpr i, int indent) throws IOException {
        writeClass(i.getClass());
        branch("target", indent, false);
        writeExpr(i.target(), indent + 1);

        branch("index", indent, true);
        writeExpr(i.index(), indent + 1);
    }

    private void writeTrifixExpr(TrifixExpr t, int indent) throws IOException {
        writeClass(t.getClass());
        branch("op", indent, false);
        write(t.op().toString());
        branch("left", indent, false);
        writeExpr(t.left(), indent + 1);
        branch("mid", indent, false);
        writeExpr(t.middle(), indent + 1);
        branch("right", indent, true);
        writeExpr(t.right(), indent + 1);
    }

    //============================================================
    // LITERALS
    //============================================================
    private void writeLiteral(LiteralNode lit, int indent) throws IOException {
        switch (lit) {
            case NullLiteralNode __ -> write("NULL");

            case BoolLiteralNode b -> writeBoolLiteral(b, indent);
            case NumberLiteralNode n -> writeNumberLiteral(n, indent);
            case StringLiteralNode s -> writeStringLiteral(s, indent);
            case BytesLiteralNode b -> writeBytesLiteral(b, indent);

            case ListLiteralNode list -> writeListLiteral(list, indent);
            case MapLiteralNode map   -> writeMapLiteral(map, indent);

            case EnumLiteralNode e -> writeEnumLiteral(e, indent);
            case UnionLiteralNode u -> writeUnionLiteral(u, indent);
            case StructLiteralNode s  -> writeStructLiteral(s, indent);
        }
    }

    private void writeBoolLiteral(BoolLiteralNode b, int indent) throws IOException {
        writeClass(b.getClass());
        branch("value", indent, true);
        write(b.value());
    }

    private void writeNumberLiteral(NumberLiteralNode n, int indent) throws IOException {
        writeClass(n.getClass());
        branch("text", indent, true);
        write(n.text());
    }

    private void writeStringLiteral(StringLiteralNode s, int indent) throws IOException {
        writeClass(s.getClass());
        branch("value", indent, true);
        write(s.value());
    }

    private void writeBytesLiteral(BytesLiteralNode b, int indent) throws IOException {
        writeClass(b.getClass());
        branch("text", indent, true);
        write(b.text());
    }

    private void writeListLiteral(ListLiteralNode list, int indent) throws IOException {
        forEach(list.elements(), "item", this::writeLiteral, indent, LiteralNode.class);
    }

    private void writeMapLiteral(MapLiteralNode map, int indent) throws IOException {
        forEach(map.entries(), "entry", this::writeMapEntry, indent, MapEntryLiteralNode.class);
    }

    private void writeMapEntry(MapEntryLiteralNode e, int indent) throws IOException {
        writeClass(e.getClass());
        branch("key", indent, false);
        writeLiteral(e.key(), indent + 1);
        branch("value", indent, true);
        writeLiteral(e.value(), indent + 1);
    }

    private void writeEnumLiteral(EnumLiteralNode e, int indent) throws IOException {
        writeClass(e.getClass());
        branch("enum", indent, false);
        writeQName(e.enumName(), indent + 1);
        branch("symbol", indent, true);
        write(e.symbol().name());
    }

    private void writeUnionLiteral(UnionLiteralNode u, int indent) throws IOException {
        writeClass(u.getClass());
        branch("union", indent, false);
        writeQName(u.unionName(), indent + 1);
        branch("member", indent, true);
        write(u.memberName().name());
    }

    private void writeStructLiteral(StructLiteralNode s, int indent) throws IOException {
        forEach(s.fields(), "field", this::writeStructFieldLiteral, indent, StructFieldLiteralNode.class);
    }

    private void writeStructFieldLiteral(StructFieldLiteralNode f, int indent) throws IOException {
        writeClass(f.getClass());
        branch("name", indent, false);
        write(f.name().name());
        branch("value", indent, true);
        writeLiteral(f.value(), indent + 1);
    }

    //============================================================
    // IDENTIFIERS & QNames
    //============================================================

    private void writeQName(QName q, int indent) throws IOException {
        writeClass(q.getClass());
        branch("fqn", indent, true);
        write(q.fullName());
    }

    private void writeIdentifier(Identifier id, int indent) throws IOException {
        writeClass(id.getClass());
        branch("name", indent, true);
        write(id.name());
    }

    //============================================================
    // FRAGMENT HELPERS
    //============================================================

    private void writeName(Identifier id, int indent, boolean last) throws IOException {
        branch("name", indent, last);
        writeIdentifier(id, indent + 1);
    }

    //============================================================
    // HELPERS
    //============================================================

    private interface ForEach<T extends AstNode> {
        void each(T t, int indent) throws IOException;
    }

    private interface IfPresent<T> {
        void value(T t, int indent) throws IOException;

    }

    private <T extends AstNode> void forEach(AstListNode<T> xs, String name, ForEach<T> fn, int indent, Class<T> clazz) throws IOException {
        writeClass(clazz.getClass());
        int size = xs.size();
        int last = size - 1;
        write("[" + size + "]");
        for (int i = 0; i < size; i++) {
            branch(name, indent, i == last);
            fn.each(xs.get(i), indent + 1);
        }
    }

    private <T extends AstNode> void writeOptional(AstOptionalNode<T> optional, IfPresent<T> ifPresent, int indent) throws IOException {
        if (optional.isPresent()) {
            ifPresent.value(optional.get(), indent + 1);
        } else {
            nil();
        }
    }
}