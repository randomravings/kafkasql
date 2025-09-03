package streamsql;
import streamsql.ast.*;
import java.util.*;
import static java.util.stream.Collectors.toList;

import streamsql.parse.SqlStreamParser;
import streamsql.parse.SqlStreamParserBaseVisitor;

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
    var qn = currentContext.append(c.identifier().getText());
    return new CreateContext(new Context(qn));
  }

  // Type decls
  @Override public Stmt visitCreateScalar(SqlStreamParser.CreateScalarContext c) {
    var name = ident(c.typeName().identifier());
    var qName = currentContext.append(name);
    var pt   = primitive(c.primitiveType());
    return new CreateType(new Complex.Scalar(qName, pt));
  }

  @Override public Stmt visitCreateEnum(SqlStreamParser.CreateEnumContext c) {
    var name = ident(c.typeName().identifier());
    var qName = currentContext.append(name);
    var syms = c.enumEntry().stream()
        .map(e -> new Complex.EnumSymbol(ident(e.identifier()), n(e.NUMBER())))
        .toList();
    return new CreateType(new Complex.Enum(qName, syms));
  }

  @Override public Stmt visitCreateStruct(SqlStreamParser.CreateStructContext c) {
    var name = ident(c.typeName().identifier());
    var qName = currentContext.append(name);
    var fs   = c.fieldDef().stream().map(this::visitStructField).toList();
    return new CreateType(new Complex.Struct(qName, fs));
  }

  @Override public Stmt visitCreateUnion(SqlStreamParser.CreateUnionContext c) {
    var name = ident(c.typeName().identifier());
    var qName = currentContext.append(name);
    var alts = c.unionAlt().stream()
        .map(a -> new Complex.UnionAlt(ident(a.identifier()), (DataType)visit(a.dataType())))
        .toList();
    return new CreateType(new Complex.Union(qName, alts));
  }

  private Complex.StructField visitStructField(SqlStreamParser.FieldDefContext f){
    String def = f.jsonString()!=null ? unquote(f.jsonString().STRING_LIT().getText()) : null;
    return new Complex.StructField(ident(f.identifier()), dataType(f.dataType()), f.OPTIONAL()!=null, def);
  }

  private DataType dataType(SqlStreamParser.DataTypeContext d){
    if (d.primitiveType()!=null) return primitive(d.primitiveType());
    if (d.compositeType()!=null) return composite(d.compositeType());
    if (d.complexType()!=null) return complex(d.complexType());
    throw new IllegalStateException("datatype");
  }

  private PrimitiveType primitive(SqlStreamParser.PrimitiveTypeContext p){
    if (p.BOOL()!=null) return Primitive.BOOL();
    if (p.INT8()!=null) return Primitive.INT8();
    if (p.UINT8()!=null) return Primitive.UINT8();
    if (p.INT16()!=null) return Primitive.INT16();
    if (p.UINT16()!=null) return Primitive.UINT16();
    if (p.INT32()!=null) return Primitive.INT32();
    if (p.UINT32()!=null) return Primitive.UINT32();
    if (p.INT64()!=null) return Primitive.INT64();
    if (p.UINT64()!=null) return Primitive.UINT64();
    if (p.SINGLE()!=null) return Primitive.SINGLE();
    if (p.DOUBLE()!=null) return Primitive.DOUBLE();
    if (p.DECIMAL()!=null) return Primitive.DECIMAL(n(p.NUMBER(0)), n(p.NUMBER(1)));
    if (p.STRING()!=null) return Primitive.STRING();
    if (p.FSTRING()!=null) return Primitive.FSTRING(n(p.NUMBER(0)));
    if (p.BYTES()!=null) return Primitive.BYTES();
    if (p.FBYTES()!=null) return Primitive.FBYTES(n(p.NUMBER(0)));
    if (p.UUID()!=null) return Primitive.UUID();
    if (p.DATE()!=null) return Primitive.DATE();
    if (p.TIME()!=null) return Primitive.TIME(n(p.NUMBER(0)));
    if (p.TIMESTAMP()!=null) return Primitive.TIMESTAMP(n(p.NUMBER(0)));
    if (p.TIMESTAMP_TZ()!=null) return Primitive.TIMESTAMP_TZ(n(p.NUMBER(0)));
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
    var alts = c.streamTypeDef().stream().map(std -> {
      if (std.inlineStruct()!=null) {
        var fs = std.inlineStruct().fieldDef().stream().map(this::visitStructField).collect(toList());
        return (StreamType.Definition) new Stream.InlineType(
                    fs,
                    ident(std.typeAlias().identifier())
        );
      } else {
        return (StreamType.Definition) new Stream.ReferenceType(
                    new TypeRef(typeRefQName(std.qname())),
                    ident(std.typeAlias().identifier()));
      }
    }).collect(toList());
    var qName = currentContext.append(ident(c.identifier()));
    var stream = c.LOG()!=null ?
      new Stream.Log(qName, alts) :
      new Stream.Compact(qName, alts)
    ;
    return new CreateStream(stream);
  }

  // READ
  @Override public Stmt visitReadStmt(SqlStreamParser.ReadStmtContext c) {
    String stream = typeRefQName(c.streamName().qname()).fullName();
    var blocks = c.typeBlock().stream().map(tb -> {
      String tn = ident(tb.typeName().identifier());
      var sel = tb.projection().projectionItem().stream().map(pi -> {
        if (pi instanceof SqlStreamParser.ProjectAllContext) return new Dml.Read.Star();
        var cols = ((SqlStreamParser.ProjectColContext)pi).columnName().identifier().stream().map(AstBuilder::ident).collect(toList());
        return new Dml.Read.Col(new Ident(cols));
      }).collect(toList());
      Expr where = tb.whereClause()!=null ? (Expr)visit(tb.whereClause().booleanExpr()) : null;
      return new Dml.Read.TypeBlock(tn, (List<Dml.Read.SelectItem>) (List<?>) sel, where);
    }).collect(toList());
    return new Dml.Read(stream, blocks);
  }

  // WRITE
  @Override public Stmt visitWriteStmt(SqlStreamParser.WriteStmtContext c) {
    String stream = typeRefQName(c.streamName().qname()).fullName();
    String typeName = ident(c.typeName().identifier());
    var proj = c.fieldPathList().fieldPath().stream().map(fp -> {
      String head = ident(fp.identifier());
      var segs = fp.pathSeg().stream().map(s -> {
        if (s.DOT()!=null) return new Dml.Write.FieldSeg(ident(s.identifier()));
        if (s.NUMBER()!=null) return new Dml.Write.IndexSeg(Integer.parseInt(s.NUMBER().getText()));
        return new Dml.Write.KeySeg(unquote(s.STRING_LIT().getText()));
      }).map(Dml.Write.PathSeg.class::cast).collect(toList());
      return new Dml.Write.Path(head, segs);
    }).collect(toList());
    var rows = c.tuple().stream().map(t -> t.literalOnlyList().valueLit().stream().map(vl -> {
      if (vl.STRING_LIT()!=null) return new Dml.Write.VStr(unquote(vl.STRING_LIT().getText()));
      if (vl.NUMBER()!=null) return new Dml.Write.VNum(Double.parseDouble(vl.NUMBER().getText()));
      if (vl.TRUE()!=null) return new Dml.Write.VBool(true);
      if (vl.FALSE()!=null) return new Dml.Write.VBool(false);
      if (vl.NULL()!=null) return Dml.Write.VNull.INSTANCE;
      return new Dml.Write.VEnum(ident(vl.identifier()));
    }).map(Dml.Write.ValLit.class::cast).collect(toList())).collect(toList());

    rows.forEach((row) -> { if (row.size()!=proj.size()) throw new IllegalArgumentException("VALUES arity mismatch"); });
    return new Dml.Write(stream, typeName, proj, rows);
  }

  // WHERE expressions
  @Override public Object visitBooleanExpr(SqlStreamParser.BooleanExprContext c) { return visit(c.orExpr()); }
  @Override public Object visitOrExpr(SqlStreamParser.OrExprContext c) { return fold(c.andExpr().stream().map(a -> (Expr)visit(a)).collect(toList()), BinOp.OR); }
  @Override public Object visitAndExpr(SqlStreamParser.AndExprContext c) { return fold(c.notExpr().stream().map(a -> (Expr)visit(a)).collect(toList()), BinOp.AND); }
  @Override public Object visitNotExpr(SqlStreamParser.NotExprContext c) { return c.NOT()!=null ? new Not((Expr)visit(c.notExpr())) : visit(c.predicate()); }
  @Override public Object visitCmpPredicate(SqlStreamParser.CmpPredicateContext c) {
    var l = (Expr)visit(c.value(0)); var r = (Expr)visit(c.value(1));
    String op = c.cmpOp().getStart().getText().toUpperCase();
    BinOp bop = switch(op){
      case "=" -> BinOp.EQ;
      case "!=","<>" -> BinOp.NEQ;
      case "<" -> BinOp.LT;
      case "<=" -> BinOp.LTE;
      case ">" -> BinOp.GT;
      case ">=" -> BinOp.GTE;
      default -> throw new IllegalStateException();
    };
    return new Binary(bop,l,r);
  }
  @Override public Object visitParenPredicate(SqlStreamParser.ParenPredicateContext c){ return visit(c.booleanExpr()); }
  @Override public Object visitValue(SqlStreamParser.ValueContext c){
    if (c.literal()!=null) return visit(c.literal());
    var parts = c.columnName().identifier().stream().map(AstBuilder::ident).collect(toList());
    return new Ident(parts);
  }
  @Override public Object visitLiteral(SqlStreamParser.LiteralContext c){
    if (c.STRING_LIT()!=null) return new Literal.Str(unquote(c.STRING_LIT().getText()));
    if (c.NUMBER()!=null) return new Literal.Num(Double.parseDouble(c.NUMBER().getText()));
    if (c.TRUE()!=null) return new Literal.Bool(true);
    if (c.FALSE()!=null) return new Literal.Bool(false);
    return Literal.Null.INSTANCE;
  }

  @Override
  public Object visitIsNullPredicate(SqlStreamParser.IsNullPredicateContext c) {
    var v = (Expr) visit(c.value());
    return new Binary(BinOp.IS_NULL, v, Literal.Null.INSTANCE);
  }

  @Override
  public Object visitIsNotNullPredicate(SqlStreamParser.IsNotNullPredicateContext c) {
    var v = (Expr) visit(c.value());
    return new Binary(BinOp.IS_NOT_NULL, v, Literal.Null.INSTANCE);
  }

  // helpers
  private static Expr fold(List<Expr> xs, BinOp op){ return xs.stream().skip(1).reduce(xs.get(0),(a,b)->new Binary(op,a,b)); }
  private static int n(org.antlr.v4.runtime.tree.TerminalNode t){ return Integer.parseInt(t.getText()); }
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

  private static String ident(SqlStreamParser.IdentifierContext id) {
    return id.ID().getText();
  }
  private static String unquote(String backticked) {
    // `foo``bar` -> foo`bar
    return backticked.substring(1, backticked.length()-1).replace("``","`");
  }
}
