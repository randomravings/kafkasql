package streamsql;

import streamsql.ast.*;
import java.io.Writer;
import java.io.IOException;
import java.util.List;

public final class AstPrinter extends Printer {

  private static final String BRANCH_PIPE_TAB = "│  ";
  private static final String BRANCH_EMPTY_TAB = "   ";
  private static final String BRANCH_LAST = "└─ ";
  private static final String BRANCH_MID = "├─ ";

  private final Boolean[] pipes = new Boolean[32];

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

  private void indentTree(int indent) throws IOException {
    for (int i = 0; i < indent; i++) {
      if (pipes[i])
        write(BRANCH_PIPE_TAB);
      else
        write(BRANCH_EMPTY_TAB);
    }
  }

  private void writeStmt(Stmt stmt, int indent) throws IOException {
    if (stmt instanceof UseStmt us) {
      writeUseStatement(us, indent + 1);
    } else if (stmt instanceof DmlStmt dml) {
      writeDmlStatement(dml, indent + 1);
    } else if (stmt instanceof DdlStmt ddl) {
      writeDdlStatement(ddl, indent + 1);
    }
  }

  private void writeQName(QName qName, int indent) throws IOException {
    writeTypeNode(QName.class);
    writeKey("fullName", indent + 1, true);
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

  private void writeDmlStatement(DmlStmt dmlStmt, int indent) throws IOException {
    switch (dmlStmt) {
      case ReadStmt r:
        writeReadStmt(r, indent);
        break;
      case WriteStmt w:
        writeWriteStmt(w, indent);
        break;
      default:
        writeTypeNode(dmlStmt.getClass());
        break;
    }
  }

  private void writeReadStmt(ReadStmt r, int indent) throws IOException {
    writeTypeNode(ReadStmt.class);
    writeKey("stream", indent, false);
    writeQName(r.stream(), indent);
    writeKey("blocks", indent, true);
    writeTypeListNode(ReadSelection.class);
    forEach(r.blocks(), (b, ind) -> writeTypeBlock(b, ind + 1), indent + 1);
  }

  private void writeTypeBlock(ReadSelection b, int indent) throws IOException {
    writeTypeNode(ReadSelection.class);
    writeKey("alias", indent, false);
    writeIdentifier(b.alias(), indent + 1);
    writeKey("projection", indent, !b.where().isPresent());
    writeProjectionList(b.projection(), indent + 1);
    if (b.where().isPresent()) {
      writeKey("where", indent, true);
      writeType(WhereClause.class);
      writeKey("filter", indent + 1, true);
      writeType(Binary.class);
      branch(indent + 2, true);
      writeExprPolish(b.where().get().filter(), indent + 2);
    }
  }

  private void writeProjectionList(Projection pl, int indent) throws IOException {
    if (pl instanceof ProjectionAll) {
      writeTypeNode(ProjectionAll.class);
    } else if (pl instanceof ProjectionList list) {
      writeTypeNode(ProjectionList.class);
      writeKey("fields", indent, true);
      writePathList(list.fields(), indent + 1);
    }
  }

  private void writePathList(List<Path> paths, int indent) throws IOException {
    writeTypeListNode(Path.class);
    forEach(paths, (p, i) -> writePath(p, i + 1), indent);
  }

  private void writeWriteStmt(WriteStmt w, int indent) throws IOException {
    writeTypeNode(WriteStmt.class);
    writeKey("stream", indent, false);
    writeQName(w.stream(), indent + 1);
    writeKey("alias", indent, false);
    writeIdentifier(w.alias(), indent + 1);
    writeKey("projection", indent, false);
    writePathList(w.projection(), indent + 1);
    writeKey("rows", indent, true);
    writeTypeListNode(Tuple.class);
    forEach(w.rows(), (row, ind) -> writeRow(row, ind + 1), indent + 1);
  }

  private void writeRow(Tuple row, int indent) throws IOException {
    writeTypeNode(Tuple.class);
    writeKey("arity", indent, false);
    write(Integer.toString(row.arity()));
    writeKey("values", indent, true);
    forEach(row.values(), (v, i) -> writeLiteral(v, i + 1), indent + 1);
  }

  private void writeLiteral(Literal<?, ?> v, int indent) throws IOException {
    writeTypeNode(v.getClass());
    writeKey("value", indent, true);
    write(valToString(v));
  }

  private void writeDdlStatement(DdlStmt ddl, int indent) throws IOException {
    switch (ddl) {
      case CreateStmt cs:
        writeCreateStatement(cs, indent);
        break;
      default:
        writeTypeNode(ddl.getClass());
        break;
    }
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
    colon();
    branch(indent, false);
    write("name: " + alt.name());
    branch(indent, true);
    write("type: ");
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
    writeQName(c.qName(), indent);
  }

  // Helpers for DataType, Path, Value, Expr
  private void writeDataType(DataType t, int indent) throws IOException {
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
      case FStringT f:
        writeKey("size", indent, true);
        writeNumber(f.size(), indent + 1);
        break;
      case FBytesT f:
        writeKey("size", indent, true);
        writeNumber(f.size(), indent + 1);
        break;
      case TimeT t:
        writeKey("precision", indent, true);
        writeNumber(t.precision(), indent + 1);
        break;
      case TimestampT t:
        writeKey("precision", indent, true);
        writeNumber(t.precision(), indent + 1);
        break;
      case TimestampTzT t:
        writeKey("precision", indent, true);
        writeNumber(t.precision(), indent + 1);
        break;
      case DecimalT d:
        writeKey("precision", indent, false);
        writeNumber(d.precision(), indent + 1);
        writeKey("scale", indent, true);
        writeNumber(d.scale(), indent + 1);
        break;
      default:
        break;
    }
  }

  private void writeComposite(CompositeType ct, int indent) throws IOException {
    if (ct instanceof Composite.List l) {
      writeTypeNode(Composite.List.class);
      branch(indent, true);
      writeDataType(l.item(), indent + 1);
    } else if (ct instanceof Composite.Map m) {
      writeTypeNode(Composite.Map.class);
      branch(indent, false);
      writePrimitive(m.key(), indent + 1);
      branch(indent, true);
      writeDataType(m.value(), indent + 1);
    }
  }

  private void writeComplex(ComplexType ct, int indent) throws IOException {
    writeTypeNode(ct.getClass());
    writeKey("type", indent, true);
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
    writeQName(s.qName(), indent);
    writeKey("type", indent, true);
    writePrimitive(s.primitive(), indent + 1);
  }

  private void writeEnum(streamsql.ast.Enum e, int indent) throws IOException {
    writeTypeNode(streamsql.ast.Enum.class);
    writeKey("qName", indent, false);
    writeQName(e.qName(), indent);
    writeKey("symbols", indent, true);
    writeTypeListNode(EnumSymbol.class);
    forEach(e.symbols(), (s, i) -> writeEnumSymbol(s, i + 1), indent + 1);
  }

  private void writeEnumSymbol(EnumSymbol s, int indent) throws IOException {
    writeTypeNode(EnumSymbol.class);
    writeKey("symbol", indent, false);
    writeIdentifier(s.name(), indent + 1);
    writeKey("value", indent, true);
    writeNumber(s.value(), indent + 1);
  }

  private void writeStructType(Struct st, int indent) throws IOException {
    writeTypeNode(Struct.class);
    writeKey("qName", indent, false);
    writeQName(st.qName(), indent + 1);
    writeKey("fields", indent, true);
    writeTypeListNode(Field.class);
    forEach(st.fields(), (f, ind) -> writeStructField(f, ind + 1), indent + 1);
  }

  private void writeStructField(Field f, int indent) throws IOException {
    writeTypeNode(Field.class);
    writeKey("name", indent, false);
    writeIdentifier(f.name(), indent + 1);
    writeKey("type", indent, false);
    writeDataType(f.typ(), indent + 1);
    writeKey("optional", indent, false);
    write(f.optional());
    writeKey("defaultJson", indent, true);
    write(f.defaultJson());
  }

  private void writeUnion(Union u, int indent) throws IOException {
    writeTypeNode(Union.class);
    writeKey("qName", indent, false);
    writeQName(u.qName(), indent + 1);
    writeKey("types", indent, true);
    writeTypeListNode(UnionAlt.class);
    forEach(u.types(), (alt, ind) -> writeUnionAlt(alt, ind + 1), indent + 1);
  }

  private void writeTypeRef(TypeRef r, int indent) throws IOException {
    writeTypeNode(TypeRef.class);
    writeKey("qName", indent, true);
    writeQName(r.qName(), indent + 1);
  }

  private void writeStream(DataStream s, int indent) throws IOException {
    writeTypeNode(s.getClass());
    writeKey("qName", indent, false);
    writeQName(s.qName(), indent);
    writeKey("types", indent, true);
    writeTypeListNode(StreamType.class);
    forEach(s.types(), (def, ind) -> writeStreamType(def, ind + 1), indent + 1);
  }

  private void writeStreamType(StreamType streamType, int indent) throws IOException {
    writeTypeNode(streamType.getClass());
    writeKey("alias", indent, false);
    writeIdentifier(streamType.alias(), indent + 1);
    if (streamType instanceof StreamInlineT it) {
      writeKey("fields", indent, false);
      writeTypeListNode(Field.class);
      forEach(it.fields(), (f, ind) -> writeStructField(f, ind + 1), indent + 1);
    } else if (streamType instanceof StreamReferenceT rt) {
      writeKey("ref", indent, false);
      writeTypeRef(rt.ref(), indent + 1);
    }
    writeKey("distributionKeys", indent, true);
    forEach(streamType.distributionKeys(), (k, ind) -> writeIdentifier(k, ind + 1), indent + 1);
  }

  private void writeNumber(Numeric<?, ?> n, int indent) throws IOException {
    writeTypeNode(n.getClass());
    writeKey("value", indent, true);
    write(numToString(n));
  }

  private void writeIdentifier(Identifier id, int indent) throws IOException {
    writeTypeNode(Identifier.class);
    writeKey("value", indent, true);
    write(id.value());
  }

  private void writePath(Path p, int indent) throws IOException {
    writeTypeNode(Path.class);
    writeKey("fullName", indent, true);
    write(p.fullName());
  }

  private static String valToString(Literal<?, ?> v) {
    switch (v) {
      case NullV __:
        return "NULL";
      case BoolV b:
        return Boolean.toString(b.value());
      case Numeric<?, ?> n:
        return numToString(n);
      case Chars<?, ?> c:
        return chrToString(c);
      case Temporal<?, ?> t:
        return tmpToString(t);
      case UuidV u:
        return u.value().toString();
      case Path p:
        return p.fullName();
      case Identifier id:
        return id.value();
      default:
        return v.toString();
    }
  }

  private static String numToString(Numeric<?, ?> n) {
    switch (n) {
      case Int8V v:
        return Long.toString(v.value());
      case Int16V v:
        return Long.toString(v.value());
      case Int32V v:
        return Long.toString(v.value());
      case Int64V v:
        return Long.toString(v.value());
      case Float32V v:
        return Float.toString(v.value());
      case Float64V v:
        return Double.toString(v.value());
      default:
        return n.toString();
    }
  }

  private static String chrToString(Chars<?, ?> s) {
    switch (s) {
      case StringV v:
        return "'" + v.value() + "'";
      case FStringV v:
        return "f'" + v.value() + "'";
      case BytesV v:
        return "0x" + bytesToHex(v.value());
      case FBytesV v:
        return "f'" + bytesToHex(v.value()) + "'";
      default:
        return s.toString();
    }
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  private static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  private static String tmpToString(Temporal<?, ?> t) {
    switch (t) {
      case DateV v:
        return v.toString();
      case TimeV v:
        return v.toString();
      case TimestampV v:
        return v.toString();
      case TimestampTzV v:
        return v.toString();
      default:
        return t.toString();
    }
  }

  private void writeExprPolish(Expr e, int indent) throws IOException {
    if (e instanceof Unary u) {
        lbracket();
        write(switch (u.op()) {
            case NEG -> "-";
            case NOT -> "NOT";
        });
        comma();
        space();
        writeExprPolish(u.expr(), indent + 1);
        rbracket();
        return;
    }
    if (e instanceof Binary b) {
        String op = switch (b.op()) {
            case EQ -> "EQ";
            case NEQ -> "NEQ";
            case LT -> "LT";
            case LTE -> "LTE";
            case GT -> "GT";
            case GTE -> "GTE";
            case AND -> "AND";
            case OR -> "OR";
            case IS_NULL -> "IS_NULL";
            case IS_NOT_NULL -> "IS_NOT_NULL";
        };
        boolean leftIsLiteral = b.left() instanceof Literal<?, ?>;
        boolean rightIsLiteral = b.right() instanceof Literal<?, ?>;
        if (b.op() == BinaryOp.IS_NULL || b.op() == BinaryOp.IS_NOT_NULL) {
            lbracket();
            write(op);
            comma();
            space();
            writeExprPolish(b.left(), indent + 1);
            rbracket();
        } else if (leftIsLiteral && rightIsLiteral) {
            lbracket();
            write(op);
            comma();
            space();
            writeExprPolish(b.left(), indent + 1);
            comma();
            space();
            writeExprPolish(b.right(), indent + 1);
            rbracket();
        } else {
            lbracket();
            write(op);
            comma();
            newLine();
            indentTree(indent + 2);
            writeExprPolish(b.left(), indent + 1);
            comma();
            newLine();
            indentTree(indent + 2);
            writeExprPolish(b.right(), indent + 1);
            newLine();
            indentTree(indent + 1);
            rbracket();
        }
        return;
    }
    if (e instanceof Literal<?, ?> l) {
        write(valToString(l));
        return;
    }
    write("NULL");
  }

  // Utility for forEach with last-element detection
  private interface ForEach<T> {
    void each(T t, int indent) throws IOException;
  }

  private <T> void forEach(List<T> xs, ForEach<T> fn, int indent) throws IOException {
    int size = xs.size();
    if (size == 0) {
      write("<empty>");
    }
    else {
      int last = size - 1;
      for (int i = 0; i < size; i++) {
        branch(indent, i == last);
        fn.each(xs.get(i), indent);
      }
    }
  }
}