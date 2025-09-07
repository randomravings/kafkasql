package streamsql;
import streamsql.ast.*;
import streamsql.ast.Enum;

import java.util.*;

import org.antlr.v4.runtime.tree.TerminalNode;

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import streamsql.parse.SqlStreamParser;
import streamsql.parse.SqlStreamParserBaseVisitor;
import streamsql.parse.SqlStreamParser.FieldPathContext;

public class AstBuilder extends SqlStreamParserBaseVisitor<Object> {

  private QName currentContext = QName.root();

  @Override public List<Stmt> visitScript(SqlStreamParser.ScriptContext ctx) {
    var x = ctx.statement().stream().map(s -> (Stmt)visit(s)).collect(toList());
    return x; 
  }

  @Override public Stmt visitStatement(SqlStreamParser.StatementContext ctx) {
    if (ctx.useStmt()!=null) return (Stmt)visit(ctx.useStmt());
    if (ctx.dmlStmt()!=null) return (Stmt)visit(ctx.dmlStmt());
    if (ctx.ddlStmt()!=null) return (Stmt)visit(ctx.ddlStmt());
    throw new IllegalStateException("unknown statement");
  }

  // Contexts
  @Override public Stmt visitUseContext(SqlStreamParser.UseContextContext c) {
    var qn = contextQName(currentContext, c.qname());
    currentContext = qn;
    return new UseContext(new Context(qn));
  }

  @Override public Stmt visitCreateContext(SqlStreamParser.CreateContextContext c) {
    var qn = currentContext.append(ident(c.identifier()));
    return new CreateContext(new Context(qn));
  }

  // Type decls
  @Override public Stmt visitCreateScalar(SqlStreamParser.CreateScalarContext c) {
    Identifier name = ident(c.typeName().identifier());
    QName qName = currentContext.append(name);
    PrimitiveType pt = primitive(c.primitiveType());
    return new CreateScalar(new Scalar(qName, pt));
  }

  @Override public Stmt visitCreateEnum(SqlStreamParser.CreateEnumContext c) {
    Identifier name = ident(c.typeName().identifier());
    QName qName = currentContext.append(name);
    var syms = c.enumSymbol().stream()
        .map(e -> new EnumSymbol(ident(e.identifier()), int32V(e.INT32_V())))
        .toList();
    return new CreateEnum(new Enum(qName, syms));
  }

  @Override public Stmt visitCreateStruct(SqlStreamParser.CreateStructContext c) {
    var name = ident(c.typeName().identifier());
    var qName = currentContext.append(name);
    var fs   = c.fieldDef().stream().map(this::visitStructField).toList();
    return new CreateStruct(new Struct(qName, fs));
  }

  @Override public Stmt visitCreateUnion(SqlStreamParser.CreateUnionContext c) {
    var name = ident(c.typeName().identifier());
    var qName = currentContext.append(name);
    var alts = c.unionAlt().stream()
        .map(a -> {
          Identifier aName = ident(a.identifier());
          DataType aType = dataType(a.dataType());
          return new UnionAlt(aName, aType);
        })
        .toList();
    return new CreateUnion(new Union(qName, alts));
  }

  private Field visitStructField(SqlStreamParser.FieldDefContext f){
    String def = f.jsonString()!=null ? unquote(f.jsonString().STRING_V().getText()) : null;
    return new Field(ident(f.identifier()), dataType(f.dataType()), f.OPTIONAL()!=null, def);
  }

  private DataType dataType(SqlStreamParser.DataTypeContext d){
    if (d.primitiveType()!=null) return primitive(d.primitiveType());
    if (d.compositeType()!=null) return composite(d.compositeType());
    if (d.complexType()!=null) return complex(d.complexType());
    throw new IllegalStateException("datatype");
  }

  private PrimitiveType primitive(SqlStreamParser.PrimitiveTypeContext p){
    if (p.BOOL()!=null) return BoolT.get();
    if (p.INT8()!=null) return Int8T.get();
    if (p.UINT8()!=null) return UInt8T.get();
    if (p.INT16()!=null) return Int16T.get();
    if (p.UINT16()!=null) return UInt16T.get();
    if (p.INT32()!=null) return Int32T.get();
    if (p.UINT32()!=null) return UInt32T.get();
    if (p.INT64()!=null) return Int64T.get();
    if (p.UINT64()!=null) return UInt64T.get();
    if (p.FLOAT32()!=null) return Float32T.get();
    if (p.FLOAT64()!=null) return Float64T.get();
    if (p.DECIMAL()!=null) return DecimalT.get(int32V(p.INT32_V(0)), int32V(p.INT32_V(1)));
    if (p.STRING()!=null) return StringT.get();
    if (p.FSTRING()!=null) return FStringT.get(int32V(p.INT32_V(0)));
    if (p.BYTES()!=null) return BytesT.get();
    if (p.FBYTES()!=null) return FBytesT.get(int32V(p.INT32_V(0)));
    if (p.UUID()!=null) return UuidT.get();
    if (p.DATE()!=null) return DateT.get();
    if (p.TIME()!=null) return TimeT.get(int32V(p.INT32_V(0)));
    if (p.TIMESTAMP()!=null) return TimestampT.get(int32V(p.INT32_V(0)));
    if (p.TIMESTAMP_TZ()!=null) return TimestampTzT.get(int32V(p.INT32_V(0)));
    throw new IllegalStateException("primitive");
  }

  private DataType composite(SqlStreamParser.CompositeTypeContext c){
    if (c.LIST()!=null) return new Composite.List(dataType(c.dataType()));
    var key = primitive(c.primitiveType());
    var val = dataType(c.dataType());
    return new Composite.Map(key, val);
  }

  private DataType complex(SqlStreamParser.ComplexTypeContext c){
    var qn = typeRefQName(c.qname());
    return new TypeRef(qn);
  }

  // CREATE STREAM
  @Override
  public Stmt visitCreateStream(SqlStreamParser.CreateStreamContext c) {
    List<StreamType> alts = c.streamTypeDef().stream().map(std -> {
      List<Identifier> dist =
        std.distributionClause()==null
          ? List.of()
          : std.distributionClause().identifier().stream()
              .map(AstBuilder::ident)
              .toList();

      if (std.inlineStruct()!=null) {
        var fs = std.inlineStruct().fieldDef().stream()
            .map(this::visitStructField)
            .collect(toList());
        return new StreamInlineT(
          fs,
          ident(std.typeAlias().identifier()),
          dist
        );
      } else {
        return new StreamReferenceT(
          new TypeRef(typeRefQName(std.qname())),
          ident(std.typeAlias().identifier()),
          dist
        );
      }
    }).collect(toList());
    var qName = currentContext.append(ident(c.identifier()));
    var stream = c.LOG()!=null
      ? new StreamLog(qName, alts)
      : new StreamCompact(qName, alts);
    return new CreateStream(stream);
  }

  // READ
  @Override public Stmt visitReadStmt(SqlStreamParser.ReadStmtContext c) {
    QName stream = typeRefQName(c.streamName().qname());
    List<ReadSelection> blocks = c.typeBlock().stream().map(tb -> {
      Identifier tn = ident(tb.typeName().identifier());
      Projection proj = (tb.projection().getStart().getType() == SqlStreamParser.STAR)
        ? ProjectionAll.getInstance()
        : new ProjectionList(
            tb.projection().fieldPath().stream().map(pi -> {
              return visitFieldPath(pi);
          }).collect(toList()));
      Optional<WhereClause> where = tb.whereClause()!=null ? Optional.of(new WhereClause((Binary)visit(tb.whereClause().booleanExpr()))) : Optional.empty();
      return new ReadSelection(tn, proj, where);
      }).collect(toList());
    return new ReadStmt(stream, blocks);
  }

  // WRITE
  @Override public Stmt visitWriteStmt(SqlStreamParser.WriteStmtContext c) {
    QName stream = typeRefQName(c.streamName().qname());
    Identifier typeName = ident(c.typeName().identifier());
    List<Path> proj = c.fieldPathList().fieldPath().stream().map(fp -> {
      return getPath(fp);
    }).collect(toList());
    List<Tuple> rows = c.tuple().stream().map(vr -> {
      return new Tuple(vr.literalOnlyList().literal().stream().map(lit -> visitLiteral(lit)).collect(toList()));
    }).collect(toList());

    rows.forEach((row) -> { if (row.values().size()!=proj.size()) throw new IllegalArgumentException("VALUES arity mismatch"); });
    return new WriteStmt(stream, typeName, proj, rows);
  }

  private static Path getPath(FieldPathContext c) {
    Identifier head = ident(c.identifier());
    var segs = new ArrayList<PathSeg>();
    segs.add(new PathFieldSeg(head));
    if (c.fieldPathSeg()!=null) {
      for (var s : c.fieldPathSeg()) {
        if (s.DOT()!=null) segs.add(new PathFieldSeg(ident(s.identifier())));
        else if (s.INT32_V()!=null) segs.add(new PathIndexSeg(Integer.parseInt(s.INT32_V().getText())));
        else if (s.STRING_V()!=null) segs.add(new PathKeySeg(unquote(s.STRING_V().getText())));
        else throw new IllegalStateException("unknown path segment");
      }
    }
    return new Path(segs);
  }

  // WHERE expressions
  @Override public Object visitBooleanExpr(SqlStreamParser.BooleanExprContext c) { return visit(c.orExpr()); }
  @Override public Object visitOrExpr(SqlStreamParser.OrExprContext c) { return fold(c.andExpr().stream().map(a -> (Expr)visit(a)).collect(toList()), BinaryOp.OR); }
  @Override public Object visitAndExpr(SqlStreamParser.AndExprContext c) { return fold(c.notExpr().stream().map(a -> (Expr)visit(a)).collect(toList()), BinaryOp.AND); }
  @Override public Object visitNotExpr(SqlStreamParser.NotExprContext c) { return c.NOT()!=null ? new Unary(UnaryOp.NOT, (Expr)visit(c.notExpr())) : visit(c.predicate()); }
  @Override public Object visitCmpPredicate(SqlStreamParser.CmpPredicateContext c) {
    var l = (Expr)visit(c.value(0));
    var r = (Expr)visit(c.value(1));
    String op = c.cmpOp().getStart().getText().toUpperCase();
    BinaryOp bop = switch(op){
      case "=" -> BinaryOp.EQ;
      case "!=","<>" -> BinaryOp.NEQ;
      case "<" -> BinaryOp.LT;
      case "<=" -> BinaryOp.LTE;
      case ">" -> BinaryOp.GT;
      case ">=" -> BinaryOp.GTE;
      default -> throw new IllegalStateException();
    };
    return new Binary(bop,l,r);
  }
  @Override public Object visitParenPredicate(SqlStreamParser.ParenPredicateContext c){ return visit(c.booleanExpr()); }
  @Override public Object visitValue(SqlStreamParser.ValueContext c){
    if (c.literal()!=null) return visit(c.literal());
    return visitFieldPath(c.fieldPath());
  }
  @Override public Path visitFieldPath(SqlStreamParser.FieldPathContext c){
    Identifier head = ident(c.identifier());
    var segs = new ArrayList<PathSeg>();
    segs.add(new PathFieldSeg(head));
    if (c.fieldPathSeg()!=null) {
      for (var s : c.fieldPathSeg()) {
        if (s.DOT()!=null) segs.add(new PathFieldSeg(ident(s.identifier())));
        else if (s.INT32_V()!=null) segs.add(new PathIndexSeg(Integer.parseInt(s.INT32_V().getText())));
        else if (s.STRING_V()!=null) segs.add(new PathKeySeg(unquote(s.STRING_V().getText())));
        else throw new IllegalStateException("unknown path segment");
      }
    }
    return new Path(segs);
  }

  @Override public Literal<?, ?> visitLiteral(SqlStreamParser.LiteralContext c){
    if(c.nullLiteral()!=null) return NullV.INSTANCE;
    if(c.primitiveLiteral()!=null) return visitPrimitiveLiteral(c.primitiveLiteral());
    throw new IllegalStateException("unknown literal");
  }

  @Override public Literal<?, ?> visitPrimitiveLiteral(SqlStreamParser.PrimitiveLiteralContext c){
    if(c.booleanLiteral()!=null) return visitBooleanLiteral(c.booleanLiteral());
    if(c.numberLiteral()!=null) return visitNumberLiteral(c.numberLiteral());
    if(c.characterLiteral()!=null) return visitCharacterLiteral(c.characterLiteral());
    if(c.uuidLiteral()!=null) return new UuidV(UUID.fromString(c.uuidLiteral().UUID_V().getText()));
    if(c.temporalLiteral()!=null) return visitTemporalLiteral(c.temporalLiteral());
    throw new IllegalStateException("unknown primitive literal");
  }

  @Override public BoolV visitBooleanLiteral(SqlStreamParser.BooleanLiteralContext c){
    if (c.TRUE()!=null) return new BoolV(true);
    if (c.FALSE()!=null) return new BoolV(false);
    throw new IllegalStateException("boolean literal");
  }

  @Override public Numeric<?, ?> visitNumberLiteral(SqlStreamParser.NumberLiteralContext c){
    if (c.INT8_V()!=null) return int8V(c.INT8_V());
    if (c.UINT8_V()!=null) return uint8V(c.UINT8_V());
    if (c.INT16_V()!=null) return int16V(c.INT16_V());
    if (c.UINT16_V()!=null) return uint16V(c.UINT16_V());
    if (c.INT32_V()!=null) return int32V(c.INT32_V());
    if (c.UINT32_V()!=null) return uint32V(c.UINT32_V());
    if (c.INT64_V()!=null) return int64V(c.INT64_V());
    if (c.UINT64_V()!=null) return uint64V(c.UINT64_V());
    if (c.FLOAT32_V()!=null) return float32V(c.FLOAT32_V());
    if (c.FLOAT64_V()!=null) return float64V(c.FLOAT64_V());
    if (c.DECIMAL_V()!=null) return decimalV(c.DECIMAL_V());
    throw new IllegalStateException("number literal");
  }

  @Override public Literal<?, ?> visitCharacterLiteral(SqlStreamParser.CharacterLiteralContext c){
    if (c.STRING_V()!=null) return string(c.STRING_V());
    if (c.FSTRING_V()!=null) return fstring(c.FSTRING_V());
    if (c.BYTES_V()!=null) return bytes(c.BYTES_V());
    if (c.FBYTES_V()!=null) return fbytes(c.FBYTES_V());
    throw new IllegalStateException("character literal");
  }

  @Override public Literal<?, ?> visitTemporalLiteral(SqlStreamParser.TemporalLiteralContext c){
    if (c.DATE_V()!=null) return dateV(c.DATE_V());
    if (c.TIME_V()!=null) return timeV(c.TIME_V());
    if (c.TIMESTAMP_V()!=null) return timestampV(c.TIMESTAMP_V());
    if (c.TIMESTAMP_TZ_V()!=null) return timestampTzV(c.TIMESTAMP_TZ_V());
    throw new IllegalStateException("temporal literal");
  }


  @Override
  public Object visitIsNullPredicate(SqlStreamParser.IsNullPredicateContext c) {
    var v = (Expr) visit(c.value());
    return new Binary(BinaryOp.IS_NULL, v, NullV.INSTANCE);
  }

  @Override
  public Object visitIsNotNullPredicate(SqlStreamParser.IsNotNullPredicateContext c) {
    var v = (Expr) visit(c.value());
    return new Binary(BinaryOp.IS_NOT_NULL, v, NullV.INSTANCE);
  }

  // helpers
  private static Expr fold(List<Expr> xs, BinaryOp op){ return xs.stream().skip(1).reduce(xs.get(0),(a,b)->new Binary(op,a,b)); }
  // Build qname for context operations (CREATE/USE CONTEXT) with relative semantics.
  private QName contextQName(QName cc, SqlStreamParser.QnameContext q) {
    var parts = q.identifier().stream().map(AstBuilder::ident).toList();
    if(q.getStart().getType() == SqlStreamParser.DOT)
      return QName.of(parts);
    else
      return QName.join(cc, QName.of(parts));
  }

  // Type references are always absolute
  private QName typeRefQName(SqlStreamParser.QnameContext q) {
    var parts = q.identifier().stream().map(AstBuilder::ident).toList();
    return QName.of(parts);
  }

  private static Int8V int8V(TerminalNode v) { return new Int8V(Byte.parseByte(v.getText())); }
  private static UInt8V uint8V(TerminalNode v) { return new UInt8V(Short.parseShort(v.getText())); }
  private static Int16V int16V(TerminalNode v) { return new Int16V(Short.parseShort(v.getText())); }
  private static UInt16V uint16V(TerminalNode v) { return new UInt16V(Integer.parseInt(v.getText())); }
  private static Int32V int32V(TerminalNode v) { return new Int32V(Integer.parseInt(v.getText())); }
  private static UInt32V uint32V(TerminalNode v) { return new UInt32V(Long.parseLong(v.getText())); }
  private static Int64V int64V(TerminalNode v) { return new Int64V(Long.parseLong(v.getText())); }
  private static UInt64V uint64V(TerminalNode v) { return new UInt64V(new java.math.BigInteger(v.getText())); }
  private static Float32V float32V(TerminalNode v) { return new Float32V(Float.parseFloat(v.getText())); }
  private static Float64V float64V(TerminalNode v) { return new Float64V(Double.parseDouble(v.getText())); }
  private static DecimalV decimalV(TerminalNode v) {
    BigDecimal bd = new BigDecimal(v.getText());
    return new DecimalV(bd, bd.precision(), bd.scale());
  }

  private static StringV string(TerminalNode v) { return new StringV(unquote(v.getText())); }
  private static FStringV fstring(TerminalNode v) {
    var x = unquote(v.getText());
    return new FStringV(x, x.length());
  }
  private static BytesV bytes(TerminalNode v) { return new BytesV(unquote(v.getText()).getBytes()); }
  private static FBytesV fbytes(TerminalNode v) {
    var x = v.getText().substring(2);
    return new FBytesV(x.getBytes(), x.length());
  }

  private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS");
  private static DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSSZ");
  private static DateTimeFormatter zonedDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSSXXX");

  private static DateV dateV(TerminalNode v)
  {
    LocalDate x = LocalDate.parse(v.getText(), formatter);
    return new DateV(x);
  }

  private static TimeV timeV(TerminalNode v)
  {
    LocalTime x = LocalTime.parse(v.getText(), timeFormatter);
    return new TimeV(x);
  }

  private static TimestampV timestampV(TerminalNode v)
  {
    LocalDateTime x = LocalDateTime.parse(v.getText(), timestampFormatter);
    return new TimestampV(x);
  }

  private static TimestampTzV timestampTzV(TerminalNode v)
  {
    ZonedDateTime x = ZonedDateTime.parse(v.getText(), zonedDateTimeFormatter);
    return new TimestampTzV(x);
  }

  private static Identifier ident(SqlStreamParser.IdentifierContext id) {
    return new Identifier(id.ID().getText());
  }
  private static String unquote(String backticked) {
    // `foo``bar` -> foo`bar
    return backticked.substring(1, backticked.length()-1).replace("``","`");
  }
}
