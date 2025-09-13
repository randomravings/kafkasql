package streamsql;

import streamsql.ast.*;
import java.io.Writer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  public void write(List<Stmt> stmts) throws IOException {
    write("ast");
    colon();
    space();
    writeTypeListNode(Stmt.class);
    forEach(stmts, this::writeStmt, 0);
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
    writeContext(useContext.context(), indent + 1);
  }

  private void writeReadStmt(ReadStmt r, int indent) throws IOException {
    writeTypeNode(ReadStmt.class);
    writeKey("stream", indent, false);
    writeQName(r.stream(), indent + 1);
    writeKey("blocks", indent, true);
    writeTypeListNode(ReadSelection.class);
    forEach(r.blocks(), this::writeTypeBlock, indent + 1);
  }

  private void writeTypeBlock(ReadSelection b, int indent) throws IOException {
    writeTypeNode(ReadSelection.class);
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
      writeTypeListNode(Accessor.class);
      forEach(list.fields(), this::writeAccessor, indent + 1);
    }
  }

  private void writeWriteStmt(WriteStmt w, int indent) throws IOException {
    writeTypeNode(WriteStmt.class);
    writeKey("stream", indent, false);
    writeQName(w.stream(), indent + 1);
    writeKey("alias", indent, false);
    writeIdentifier(w.alias(), indent + 1);
    writeKey("projection", indent, false);
    writeProjection(w.projection(), indent + 1);
    writeKey("rows", indent, true);
    writeTypeListNode(ListV.class);
    forEach(w.rows(), (row, ind) -> writeRow(row, ind), indent + 1);
  }

  private void writeRow(ListV row, int indent) throws IOException {
    writeTypeNode(ListV.class);
    writeKey("values", indent, true);
    forEach(row.values(), (v, i) -> writeValue(v, i), indent + 1);
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

  private void writeUnionAlt(UnionAlt alt, int indent) throws IOException {
    writeTypeNode(UnionAlt.class);
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
      case PrimitiveType p:
        writePrimitive(p, indent);
        break;
      case CompositeType ct:
        writeComposite(ct, indent);
        break;
      case ComplexType ct:
        writeComplex(ct, indent);
        break;
      case TypeRef r:
        writeTypeRef(r, indent);
        break;
      default:
        writeTypeNode(t.getClass());
        break;
    }
  }

  private void writePrimitive(PrimitiveType p, int indent) throws IOException {
    write(p.getClass().getSimpleName());
    switch (p) {
      case CharT f:
        writeKey("size", indent, true);
        writeLiteral(f.size(), indent + 1);
        break;
      case FixedT f:
        writeKey("size", indent, true);
        writeLiteral(f.size(), indent + 1);
        break;
      case TimeT t:
        writeKey("precision", indent, true);
        writeLiteral(t.precision(), indent + 1);
        break;
      case TimestampT t:
        writeKey("precision", indent, true);
        writeLiteral(t.precision(), indent + 1);
        break;
      case TimestampTzT t:
        writeKey("precision", indent, true);
        writeLiteral(t.precision(), indent + 1);
        break;
      case DecimalT d:
        writeKey("precision", indent, false);
        writeLiteral(d.precision(), indent + 1);
        writeKey("scale", indent, true);
        writeLiteral(d.scale(), indent + 1);
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

  private void writeComposite(CompositeType ct, int indent) throws IOException {
    if (ct instanceof ListT l) {
      writeTypeNode(ListT.class);
      branch(indent, true);
      writeDataType(l.item(), indent + 1);
    } else if (ct instanceof MapT m) {
      writeTypeNode(MapT.class);
      branch(indent, false);
      writePrimitive(m.key(), indent + 1);
      branch(indent, true);
      writeDataType(m.value(), indent + 1);
    }
  }

  private void writeComplex(ComplexType ct, int indent) throws IOException {
    switch (ct) {
      case streamsql.ast.Enum e:
        writeEnum(e, indent);
        break;
      case Scalar s:
        writeScalar(s, indent);
        break;
      case Struct st:
        writeStructType(st, indent);
        break;
      case Union u:
        writeUnion(u, indent);
        break;
      default:
        writeTypeNode(ct.getClass());
        break;
    }
  }

  private void writeScalar(Scalar s, int indent) throws IOException {
    writeTypeNode(Scalar.class);
    writeKey("qName", indent, false);
    writeQName(s.qName(), indent + 1);
    writeKey("type", indent, false);
    writePrimitive(s.primitive(), indent + 1);
    writeKey("check", indent, false);
    writeOptional(s.validation(), (v, i) -> writeExpr(v, i), indent);
    writeKey("default", indent, true);
    writeOptional(s.defaultValue(), (v, i) -> writeLiteral(v, i), indent);
  }

  private void writeEnum(streamsql.ast.Enum e, int indent) throws IOException {
    writeTypeNode(streamsql.ast.Enum.class);
    writeKey("qName", indent, false);
    writeQName(e.qName(), indent + 1);
    writeKey("type", indent, false);
    writePrimitive(e.type(), indent + 1);
    writeKey("isMask", indent, false);
    writeLiteral(e.isMask(), indent + 1);
    writeKey("symbols", indent, false);
    writeTypeListNode(EnumSymbol.class);
    forEach(e.symbols(), (s, i) -> writeEnumSymbol(s, i), indent + 1);
    writeKey("default", indent, true);
    writeOptional(e.defaultSymbol(), (v, i) -> writeIdentifier(v, i), indent);
  }

  private void writeEnumSymbol(EnumSymbol s, int indent) throws IOException {
    writeTypeNode(EnumSymbol.class);
    writeKey("symbol", indent, false);
    writeIdentifier(s.name(), indent + 1);
    writeKey("value", indent, true);
    writeLiteral(s.value(), indent + 1);
  }

  private void writeStructType(Struct st, int indent) throws IOException {
    writeTypeNode(Struct.class);
    writeKey("qName", indent, false);
    writeQName(st.qName(), indent + 1);
    writeKey("fields", indent, true);
    writeTypeListNode(Field.class);
    forEach(st.fields(), (f, i) -> writeStructField(f, i), indent + 1);
  }

  private void writeStructField(Field f, int indent) throws IOException {
    writeTypeNode(Field.class);
    writeKey("name", indent, false);
    writeIdentifier(f.name(), indent + 1);
    writeKey("type", indent, false);
    writeDataType(f.typ(), indent + 1);
    writeKey("optional", indent, false);
    writeLiteral(f.optional(), indent + 1);
    writeKey("default", indent, true);
    writeOptional(f.defaultValue(), (v, i) -> writeLiteral(v, i), indent + 1);
  }

  private void writeUnion(Union u, int indent) throws IOException {
    writeTypeNode(Union.class);
    writeKey("qName", indent, false);
    writeQName(u.qName(), indent + 1);
    writeKey("types", indent, true);
    writeTypeListNode(UnionAlt.class);
    forEach(u.types(), (a, i) -> writeUnionAlt(a, i), indent + 1);
  }

  private void writeTypeRef(TypeRef r, int indent) throws IOException {
    writeTypeNode(TypeRef.class);
    writeKey("qName", indent, true);
    writeQName(r.qName(), indent + 1);
  }

  private void writeStream(DataStream s, int indent) throws IOException {
    writeTypeNode(s.getClass());
    writeKey("qName", indent, false);
    writeQName(s.qName(), indent + 1);
    writeKey("types", indent, true);
    writeTypeListNode(StreamType.class);
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
    writeKey("distributionKeys", indent, true);
    forEach(streamType.distributionKeys(), (k, i) -> writeIdentifier(k, i), indent + 1);
  }

  private void writeIdentifier(Identifier id, int indent) throws IOException {
    writeTypeNode(Identifier.class);
    writeKey("value", indent, true);
    write(id.value());
  }

  // AccessPath printing
  private void writeAccessSegment(Segment p, int indent) throws IOException {
    writeTypeNode(Segment.class);
    writeKey("head", indent, false);
    writeAccessor(p.head(), indent + 1);
    writeKey("tail", indent, true);
    writeAccessor(p.tail(), indent + 1);
  }

  private void writeAccessor(Accessor seg, int indent) throws IOException {
    switch (seg) {
      case Identifier va -> writeIdentifier(va, indent);
      case Indexer ia -> writeLiteral(ia.index(), indent);
      case Segment ap -> writeAccessSegment(ap, indent);
    }
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
      case Unary u -> writeUnaryExpr(u, indent);
      case Binary b -> writeBinaryExpr(b, indent);
      case Literal l -> writeLiteral(l, indent);
      case Identifier id -> writeIdentifier(id, indent);
      case Segment ap -> writeAccessSegment(ap, indent);
      default -> write(e.toString());
    }
  }

  private void writeUnaryExpr(Unary u, int indent) throws IOException {
    writeTypeNode(Unary.class);
    writeKey("op", indent, false);
    write(u.op().toString());
    writeKey("expr", indent, true);
    writeExpr(u.expr(), indent + 1);
  }

  private void writeBinaryExpr(Binary b, int indent) throws IOException {
    writeTypeNode(Binary.class);
    writeKey("op", indent, false);
    write(b.op().toString());
    writeKey("left", indent, false);
    writeExpr(b.left(), indent + 1);
    writeKey("right", indent, true);
    writeExpr(b.right(), indent + 1);
  }

  private void writeValue(AnyV v, int indent) throws IOException {
    if (v == null) { nil(); return; }
    switch (v) {
      case Literal l -> writeLiteral(l, indent);
      case ListV l -> writeListV(l, indent);
      case MapV m -> writeMapV(m, indent);
      default -> write(v.toString());
    }
  }

  private void writeListV(ListV l, int indent) throws IOException {
    writeTypeNode(ListV.class);
    writeKey("values", indent, true);
    forEach(l.values(), (v, i) -> writeValue(v, i), indent + 1);
  }

  private void writeMapV(MapV m, int indent) throws IOException {
    writeTypeNode(MapV.class);
    writeKey("entries", indent, true);
    forEach(m.values(), (kv, i) -> writeMapEntry(kv, i + 1), indent + 1);
  }

  private void writeMapEntry(Map.Entry<Literal, AnyV> entry, int indent) throws IOException {
    writeTypeNode(Map.Entry.class);
    writeKey("key", indent, false);
    writeValue(entry.getKey(), indent + 1);
    writeKey("value", indent, true);
    writeValue(entry.getValue(), indent + 1);
  }

  private void writeLiteral(Literal v, int indent) throws IOException {
    if (v == null) { nil(); return; }

    writeTypeNode(v.getClass());
    writeKey("value", indent, true);

    if (v.value() == null) { nil(); return; }

    switch (v) {
      case NullV __ -> write("NULL");
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

  private <T> void writeOptional(Optional<T> optional, IfPresent<T> ifPresent, int indent) throws IOException {
    if (optional.isPresent()) {
      ifPresent.value(optional.get(), indent + 1);
    } else {
      nil();
    }
  }
}