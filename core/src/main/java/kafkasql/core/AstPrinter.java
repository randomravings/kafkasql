package kafkasql.core;

import java.io.Writer;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import kafkasql.core.ast.*;

public final class AstPrinter extends Printer {

  private static final String BRANCH_PIPE_TAB = "│  ";
  private static final String BRANCH_EMPTY_TAB = "   ";
  private static final String BRANCH_LAST = "└─ ";
  private static final String BRANCH_MID = "├─ ";

  private final Boolean[] pipes = new Boolean[64];

  public AstPrinter(Writer out) {
    super(out);
    for (int i = 0; i < pipes.length; i++)
      pipes[i] = false;
  }

  public void writeKey(String key, int indent, boolean last) throws IOException {
    branch(indent, last);
    write(key);
    colon();
    space();
  }

  public void writeTypeNode(Class<?> clazz) throws IOException {
    lt();
    write(clazz.getSimpleName());
    gt();
  }

  private void writeTypeListNode(Class<?> clazz) throws IOException {
    writeTypeNode(clazz);
    lbracket();
    rbracket();
  }

  @Override
  public void write(Ast ast) throws IOException {
    writeKey("ast", 0, true);
    writeTypeListNode(Stmt.class);
    forEach(ast, this::writeStmt, 0);
    newLine();
  }

  private void branch(int indent, boolean last) throws IOException {
    newLine();
    pipes[indent] = !last;
    for (int i = 0; i < indent; i++) {
      if (pipes[i])
        write(BRANCH_PIPE_TAB);
      else
        write(BRANCH_EMPTY_TAB);
    }
    if (last)
      write(BRANCH_LAST);
    else
      write(BRANCH_MID);
  }

  private void writeStmt(Stmt stmt, int indent) throws IOException {
    if (stmt instanceof UseStmt us) {
      writeUseStatement(us, indent);
    } else if (stmt instanceof CreateStmt createStmt) {
      writeCreateStatement(createStmt, indent);
    } else if (stmt instanceof ReadStmt readStmt) {
      writeReadStmt(readStmt, indent);
    } else if (stmt instanceof WriteStmt writeStmt) {
      writeWriteStmt(writeStmt, indent);
    } else {
      writeTypeNode(stmt.getClass());
    }
  }

  private void writeQName(QName qName, int indent) throws IOException {
    writeTypeNode(QName.class);
    writeKey("fullName", indent, true);
    write(qName.fullName());
  }

  private void writeUseStatement(UseStmt useStatement, int indent) throws IOException {
    switch (useStatement) {
      case UseContext uc:
        writeUseContext(uc, indent);
        break;
      default:
        writeTypeNode(useStatement.getClass());
        break;
    }
  }

  private void writeUseContext(UseContext useContext, int indent) throws IOException {
    writeTypeNode(UseContext.class);
    writeKey("context", indent, true);
    writeQName(useContext.qname(), indent + 1);
  }

  private void writeReadStmt(ReadStmt r, int indent) throws IOException {
    writeTypeNode(ReadStmt.class);
    writeKey("stream", indent, false);
    writeQName(r.stream(), indent + 1);
    writeKey("blocks", indent, true);
    writeTypeListNode(ReadTypeBlock.class);
    forEach(r.blocks(), this::writeTypeBlock, indent + 1);
  }

  private void writeTypeBlock(ReadTypeBlock b, int indent) throws IOException {
    writeTypeNode(ReadTypeBlock.class);
    writeKey("alias", indent, false);
    writeIdentifier(b.alias(), indent + 1);
    writeKey("projection", indent, false);
    writeProjection(b.projection(), indent + 1);
    writeKey("where", indent, true);
    writeOptional(b.where(), this::writeWhereClause, indent);
  }

  private void writeWhereClause(WhereClause whereClause, int indent) throws IOException {
    writeTypeNode(WhereClause.class);
    writeKey("expr", indent, true);
    writeExpr(whereClause.expr(), indent + 1);
  }

  private void writeProjection(Projection pl, int indent) throws IOException {
    if (pl instanceof ProjectionAll) {
      writeTypeNode(ProjectionAll.class);
    } else if (pl instanceof ProjectionList list) {
      writeTypeNode(ProjectionList.class);
      writeKey("fields", indent, true);
      writeTypeListNode(ProjectionList.class);
      forEach(list, this::writeProjectionExpr, indent + 1);
    }
  }

  private void writeProjectionExpr(ProjectionExpr pe, int indent) throws IOException {
    writeTypeNode(ProjectionExpr.class);
    writeKey("expr", indent, false);
    writeExpr(pe.expr(), indent + 1);
    writeKey("alias", indent, true);
    writeOptional(pe.alias(), this::writeIdentifier, indent);
  }

  private void writeWriteStmt(WriteStmt w, int indent) throws IOException {
    writeTypeNode(WriteStmt.class);
    writeKey("stream", indent, false);
    writeQName(w.stream(), indent + 1);
    writeKey("alias", indent, false);
    writeIdentifier(w.alias(), indent + 1);
    writeKey("rows", indent, true);
    writeTypeListNode(ListV.class);
    forEach(w.values(), (row, ind) -> writeValue(row, ind), indent + 1);
  }

  private void writeField(Map.Entry<Identifier, AnyV> field, int indent) throws IOException {
    writeTypeNode(Map.Entry.class);
    writeKey("name", indent, false);
    writeIdentifier(field.getKey(), indent + 1);
    writeKey("value", indent, true);
    writeValue(field.getValue(), indent + 1);
  }

  private void writeCreateStatement(CreateStmt createStatement, int indent) throws IOException {
    switch (createStatement) {
      case CreateContext c:
        writeCreateContext(c, indent);
        break;
      case CreateType ct:
        writeCreateType(ct, indent);
        break;
      case CreateStream cs:
        writeCreateStream(cs, indent);
        break;
      default:
        writeTypeNode(createStatement.getClass());
        break;
    }
  }

  private void writeCreateContext(CreateContext createContext, int indent) throws IOException {
    writeTypeNode(CreateContext.class);
    writeKey("context", indent, true);
    writeContext(createContext.context(), indent + 1);
  }

  private void writeCreateType(CreateType ct, int indent) throws IOException {
    writeTypeNode(CreateType.class);
    writeKey("type", indent, true);
    writeComplex(ct.type(), indent + 1);
  }

  private void writeUnionAlt(UnionMember alt, int indent) throws IOException {
    writeTypeNode(UnionMember.class);
    writeKey("name", indent, false);
    writeIdentifier(alt.name(), indent + 1);
    writeKey("type", indent, true);
    writeDataType(alt.typ(), indent + 1);
  }

  private void writeCreateStream(CreateStream cs, int indent) throws IOException {
    writeTypeNode(CreateStream.class);
    writeKey("stream", indent, true);
    writeStream(cs.stream(), indent + 1);
  }

  private void writeContext(Context c, int indent) throws IOException {
    writeTypeNode(Context.class);
    writeKey("qName", indent, true);
    writeQName(c.qName(), indent + 1);
  }

  // Helpers for DataType, Path, Value, Expr
  private void writeDataType(AnyT t, int indent) throws IOException {
    switch (t) {
      case PrimitiveT p:
        writePrimitive(p, indent);
        break;
      case CompositeT ct:
        writeComposite(ct, indent);
        break;
      case ComplexT ct:
        writeComplex(ct, indent);
        break;
      case TypeReference r:
        writeTypeRef(r, indent);
        break;
      default:
        writeTypeNode(t.getClass());
        break;
    }
  }

  private void writePrimitive(PrimitiveT p, int indent) throws IOException {
    write(p.getClass().getSimpleName());
    switch (p) {
      case CharT f:
        lparen();
        write(f.size());
        rparen();
        break;
      case FixedT f:
        lparen();
        write(f.size());
        rparen();
        break;
      case TimeT t:
        lparen();
        write(t.precision());
        rparen();
        break;
      case TimestampT t:
        lparen();
        write(t.precision());
        rparen();
        break;
      case TimestampTzT t:
        lparen();
        write(t.precision());
        rparen();
        break;
      case DecimalT d:
        lparen();
        write(d.precision());
        comma();
        space();
        write(d.scale());
        rparen();
        break;
      default:
        break;
    }
  }

  protected void write(Int8V i) throws IOException {
    write(i.value());
  }

    protected void write(Int32V l) throws IOException {
        write(l.value());
    }

  private void writeComposite(CompositeT ct, int indent) throws IOException {
    if (ct instanceof ListT l) {
      writeTypeNode(ListT.class);
      branch(indent, true);
      writeDataType(l.item(), indent + 1);
    } else if (ct instanceof MapT m) {
      writeTypeNode(MapT.class);
      branch(indent, false);
      writeDataType(m.key(), indent + 1);
      branch(indent, true);
      writeDataType(m.value(), indent + 1);
    }
  }

  private void writeComplex(ComplexT ct, int indent) throws IOException {
    switch (ct) {
      case EnumT e:
        writeEnum(e, indent);
        break;
      case ScalarT s:
        writeScalar(s, indent);
        break;
      case StructT st:
        writeStructType(st, indent);
        break;
      case UnionT u:
        writeUnion(u, indent);
        break;
      default:
        writeTypeNode(ct.getClass());
        break;
    }
  }

  private void writeScalar(ScalarT s, int indent) throws IOException {
    writeTypeNode(ScalarT.class);
    writeKey("qName", indent, false);
    writeQName(s.qName(), indent + 1);
    writeKey("type", indent, false);
    writePrimitive(s.primitive(), indent + 1);
    writeKey("check", indent, false);
    writeOptional(s.checkClause(), (v, i) -> writeCheckClause(v, i), indent);
    writeKey("default", indent, true);
    writeOptional(s.defaultValue(), (v, i) -> writeLiteralValue(v, i), indent);
  }

  private void writeCheckClause(CheckClause c, int indent) throws IOException {
    writeTypeNode(CheckClause.class);
    writeKey("name", indent, false);
    writeOptional(c.name(), this::writeIdentifier, indent);
    writeKey("expr", indent, true);
    writeExpr(c.expr(), indent + 1);
  }

  private void writeEnum(kafkasql.core.ast.EnumT e, int indent) throws IOException {
    writeTypeNode(kafkasql.core.ast.EnumT.class);
    writeKey("qName", indent, false);
    writeQName(e.qName(), indent + 1);
    writeKey("type", indent, false);
    writeOptional(e.type(), this::writePrimitive, indent);
    writeKey("symbols", indent, false);
    writeTypeListNode(EnumSymbol.class);
    forEach(e.symbols(), (s, i) -> writeEnumSymbol(s, i), indent + 1);
    writeKey("default", indent, true);
    writeOptional(e.defaultValue(), (v, i) -> writeEnumV(v, i), indent);
  }

  private void writeEnumSymbol(EnumSymbol s, int indent) throws IOException {
    writeTypeNode(EnumSymbol.class);
    writeKey("symbol", indent, false);
    writeIdentifier(s.name(), indent + 1);
    writeKey("value", indent, true);
    writeLiteralValue(s.value(), indent + 1);
  }

  private void writeStructType(StructT st, int indent) throws IOException {
    writeTypeNode(StructT.class);
    writeKey("qName", indent, false);
    writeQName(st.qName(), indent + 1);
    writeKey("fields", indent, true);
    writeTypeListNode(Field.class);
    forEach(st.fieldList(), (f, i) -> writeStructField(f, i), indent + 1);
  }

  private void writeStructField(Field f, int indent) throws IOException {
    writeTypeNode(Field.class);
    writeKey("name", indent, false);
    writeIdentifier(f.name(), indent + 1);
    writeKey("type", indent, false);
    writeDataType(f.type(), indent + 1);
    writeKey("nullable", indent, false);
    writeOptional(f.nullable(), this::writeValue, indent + 1);
    writeKey("default", indent, true);
    writeOptional(f.defaultValue(), (v, i) -> writeValue(v, i), indent + 1);
  }

  private void writeUnion(UnionT u, int indent) throws IOException {
    writeTypeNode(UnionT.class);
    writeKey("qName", indent, false);
    writeQName(u.qName(), indent + 1);
    writeKey("types", indent, true);
    writeTypeListNode(UnionMember.class);
    forEach(u.types(), (a, i) -> writeUnionAlt(a, i), indent + 1);
  }

  private void writeTypeRef(TypeReference r, int indent) throws IOException {
    writeTypeNode(TypeReference.class);
    writeKey("qName", indent, true);
    writeQName(r.qName(), indent + 1);
  }

  private void writeStream(StreamT s, int indent) throws IOException {
    writeTypeNode(s.getClass());
    writeKey("qName", indent, false);
    writeQName(s.qName(), indent + 1);
    writeKey("types", indent, true);
    writeTypeListNode(StreamT.class);
    forEach(s.types(), (d, i) -> writeStreamType(d, i), indent + 1);
  }

  private void writeStreamType(StreamType streamType, int indent) throws IOException {
    writeTypeNode(streamType.getClass());
    writeKey("alias", indent, false);
    writeIdentifier(streamType.alias(), indent + 1);
    if (streamType instanceof StreamInlineT it) {
      writeKey("fields", indent, false);
      writeTypeListNode(Field.class);
      forEach(it.fields(), (f, i) -> writeStructField(f, i), indent + 1);
    } else if (streamType instanceof StreamReferenceT rt) {
      writeKey("ref", indent, false);
      writeTypeRef(rt.ref(), indent + 1);
    }
    writeKey("distributeClause", indent, true);
    writeOptional(streamType.distributeClause(), this::writeDistributionClause, indent + 1);
  }

  private void writeDistributionClause(DistributeClause d, int indent) throws IOException {
    writeTypeNode(DistributeClause.class);
    writeKey("fields", indent, true);
    forEach(d.keys(), (f, i) -> writeIdentifier(f, i), indent + 1);
  }

  private void writeIdentifier(Identifier id, int indent) throws IOException {
    writeTypeNode(Identifier.class);
    writeKey("value", indent, true);
    write(id.name());
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  private void writeBytesToHex(byte[] bytes) throws IOException {
    for (byte b : bytes) {
      int v = b & 0xFF;
      write(HEX_ARRAY[v >>> 4]);
      write(HEX_ARRAY[v & 0x0F]);
    }
  }


  // Expr dispatch: one write method per Expr subtype
  private void writeExpr(Expr e, int indent) throws IOException {
    switch (e) {
      case PostfixExpr p -> writePostfixExpr(p, indent);
      case PrefixExpr u -> writePrefixExpr(u, indent);
      case InfixExpr b -> writeInfixExpr(b, indent);
      case Ternary t -> writeTernaryExpr(t, indent);
      case PrimitiveV l -> writeLiteralValue(l, indent);
      case MemberExpr m -> writeMemberExpr(m, indent);
      case IndexExpr i -> writeIndexExpr(i, indent);
      case IdentifierExpr id -> writeSymbol(id, indent);
      case AnyV v -> writeValue(v, indent);
    }
  }

  private void writePostfixExpr(PostfixExpr p, int indent) throws IOException {
    writeTypeNode(PostfixExpr.class);
    writeKey("expr", indent, false);
    writeExpr(p.expr(), indent + 1);
    writeKey("op", indent, true);
    write(p.op().toString());
  }

  private void writePrefixExpr(PrefixExpr u, int indent) throws IOException {
    writeTypeNode(PrefixExpr.class);
    writeKey("op", indent, false);
    write(u.op().toString());
    writeKey("expr", indent, true);
    writeExpr(u.expr(), indent + 1);
  }

  private void writeInfixExpr(InfixExpr b, int indent) throws IOException {
    writeTypeNode(InfixExpr.class);
    writeKey("op", indent, false);
    write(b.op().toString());
    writeKey("left", indent, false);
    writeExpr(b.left(), indent + 1);
    writeKey("right", indent, true);
    writeExpr(b.right(), indent + 1);
  }

  private void writeTernaryExpr(Ternary t, int indent) throws IOException {
    writeTypeNode(Ternary.class);
    writeKey("op", indent, false);
    write(t.op().toString());
    writeKey("left", indent, false);
    writeExpr(t.left(), indent + 1);
    writeKey("middle", indent, false);
    writeExpr(t.middle(), indent + 1);
    writeKey("right", indent, true);
    writeExpr(t.right(), indent + 1);
  }

  private void writeMemberExpr(MemberExpr m, int indent) throws IOException {
    writeTypeNode(MemberExpr.class);
    writeKey("expr", indent, false);
    writeExpr(m.target(), indent + 1);
    writeKey("member", indent, true);
    writeIdentifier(m.name(), indent + 1);
  }

  public void writeIndexExpr(IndexExpr i, int indent) throws IOException {
    writeTypeNode(IndexExpr.class);
    writeKey("expr", indent, false);
    writeExpr(i.index(), indent + 1);
    writeKey("index", indent, true);
    writeExpr(i.index(), indent + 1);
  }

  private void writeSymbol(IdentifierExpr s, int indent) throws IOException {
    writeTypeNode(IdentifierExpr.class);
    writeKey("name", indent, true);
    write(s.name().name());
  }

  private void writeValue(AnyV v, int indent) throws IOException {
    if (v == null) { nil(); return; }
    switch (v) {
      case PrimitiveV l -> writeLiteralValue(l, indent);
      case CompositeV l -> writeCompositeValue(l, indent);
      case ComplexV c -> writeComplexValue(c, indent);
      case NullV __ -> writeNullValue();
    }
  }

  private void writeCompositeValue(CompositeV v, int indent) throws IOException {
    switch (v) {
      case ListV l -> writeListV(l, indent);
      case MapV m -> writeMapV(m, indent);
    }
  }

  private void writeListV(ListV l, int indent) throws IOException {
    writeTypeNode(ListV.class);
    writeKey("values", indent, true);
    forEach(l, (v, i) -> writeValue(v, i), indent + 1);
  }

  private void writeMapV(MapV m, int indent) throws IOException {
    writeTypeNode(MapV.class);
    writeKey("entries", indent, true);
    forEach(m, (kv, i) -> writeMapEntry(kv, i + 1), indent + 1);
  }

  private void writeComplexValue(ComplexV v, int indent) throws IOException {
    switch (v) {
      case ScalarV s -> writeScalarV(s, indent);
      case EnumV e -> writeEnumV(e, indent);
      case StructV s -> writeStructV(s, indent);
      case UnionV u -> writeUnionV(u, indent);
    }
  }

  private void writeScalarV(ScalarV s, int indent) throws IOException {
    writeTypeNode(ScalarV.class);
    writeKey("type", indent, false);
    writeLiteralValue(s.value(), indent + 1);
    writeKey("value", indent, true);
    writeValue(s.value(), indent + 1);
  }

  private void writeEnumV(EnumV e, int indent) throws IOException {
    writeTypeNode(EnumV.class);
    writeKey("enum", indent, false);
    writeQName(e.enumName(), indent + 1);
    writeKey("symbol", indent, true);
    writeIdentifier(e.symbol(), indent + 1);
  }

  private void writeUnionV(UnionV u, int indent) throws IOException {
    writeTypeNode(UnionV.class);
    writeKey("name", indent, false);
    writeQName(u.unionName(), indent + 1);
    writeKey("member", indent, true);
    writeIdentifier(u.unionMemberName(), indent + 1);
    writeKey("value", indent, true);
    writeValue(u.value(), indent + 1);
  }

  private void writeStructV(StructV struct, int indent) throws IOException {
    writeTypeNode(StructV.class);
    writeKey("fields", indent, true);
    writeTypeListNode(Field.class);
    forEach(struct, (field, ind) -> writeField(field, ind), indent + 1);
  }

  private void writeMapEntry(Map.Entry<PrimitiveV, AnyV> entry, int indent) throws IOException {
    writeTypeNode(Map.Entry.class);
    writeKey("key", indent, false);
    writeValue(entry.getKey(), indent + 1);
    writeKey("value", indent, true);
    writeValue(entry.getValue(), indent + 1);
  }

  private void writeLiteralValue(PrimitiveV v, int indent) throws IOException {
    if (v == null) { nil(); return; }

    writeTypeNode(v.getClass());
    writeKey("value", indent, true);

    switch (v) {
      case BoolV b -> write(Boolean.toString(b.value()));
      case StringV s -> writeSq(s.value());
      case CharV f -> { writeSq(f.value()); }
      case BytesV b -> { write("0x"); writeBytesToHex(b.value()); }
      case FixedV f -> { write("0x"); writeBytesToHex(f.value()); }
      case UuidV u -> write(u.value().toString());
      case Int8V i -> { write(i.value().toString()); }
      case Int16V i -> { write(i.value().toString()); }
      case Int32V i -> write(i.value().toString());
      case Int64V i -> { write(i.value().toString()); }
      case Float32V f -> { write(f.value().toString()); }
      case Float64V f -> write(f.value().toString());
      case DecimalV d -> { write(d.value().toString()); }
      case DateV d -> { write(d.value().toString()); }
      case TimeV t -> { write(t.value().toString()); }
      case TimestampV t -> { write(t.value().toString()); }
      case TimestampTzV t -> { write(t.value().toString()); }
      default -> write(v.toString());
    }
  }

  private void writeNullValue() throws IOException {
    write("NULL");
  }

  // Utility for forEach with last-element detection
  private interface ForEach<T> {
    void each(T t, int indent) throws IOException;
  }

  private interface BiForEach<K, V> {
    void each(Map.Entry<K, V> entry, int indent) throws IOException;
  }

  private interface IfPresent<T> {
    void value(T t, int indent) throws IOException;

  }

  private <T> void forEach(List<T> xs, ForEach<T> fn, int indent) throws IOException {
    int size = xs.size();
    if (size == 0) {
      empty();
    }
    else {
      int last = size - 1;
      for (int i = 0; i < size; i++) {
        branch(indent, i == last);
        fn.each(xs.get(i), indent + 1);
      }
    }
  }

private <K, V> void forEach(Map<K, V> map, BiForEach<K, V> fn, int indent) throws IOException {
    int size = map.size();
    if (size == 0) {
      empty();
    }
    else {
      int last = size - 1;
      int i = 0;
      for (Map.Entry<K, V> entry : map.entrySet()) {
        branch(indent, i == last);
        fn.each(entry, indent + 1);
        i++;
      }
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