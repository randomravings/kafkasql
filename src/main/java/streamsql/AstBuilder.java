package streamsql;

import streamsql.ast.*;
import streamsql.ast.EnumT;
import streamsql.parse.SqlStreamParser;
import streamsql.parse.SqlStreamParserBaseVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

import static java.util.stream.Collectors.toList;

public class AstBuilder extends SqlStreamParserBaseVisitor<Object> {

  @Override
  public List<Stmt> visitScript(SqlStreamParser.ScriptContext ctx) {
    return ctx.statement().stream()
        .map(s -> (Stmt) visit(s))
        .collect(toList());
  }

  @Override
  public Stmt visitStatement(SqlStreamParser.StatementContext ctx) {
    if (ctx.useStmt() != null) return (Stmt) visit(ctx.useStmt());
    if (ctx.readStmt() != null) return (Stmt) visit(ctx.readStmt());
    if (ctx.writeStmt() != null) return (Stmt) visit(ctx.writeStmt());
    if (ctx.createContext() != null) return (Stmt) visit(ctx.createContext());
    if (ctx.createScalar() != null) return (Stmt) visit(ctx.createScalar());
    if (ctx.createEnum() != null) return (Stmt) visit(ctx.createEnum());
    if (ctx.createStruct() != null) return (Stmt) visit(ctx.createStruct());
    if (ctx.createUnion() != null) return (Stmt) visit(ctx.createUnion());
    if (ctx.createStream() != null) return (Stmt) visit(ctx.createStream());
    throw new IllegalStateException("unknown statement");
  }

  // -- Contexts --------------------------------------------------------------
  @Override
  public Stmt visitUseContext(SqlStreamParser.UseContextContext c) {
    boolean absolute = c.DOT() != null;
    QName qn = visitQname(c.qname());
    return new UseContext(new Context(qn), absolute);
  }

  @Override public QName visitQname(SqlStreamParser.QnameContext q) {
    List<Identifier> parts = q.identifier().stream().map(this::visitIdentifier).collect(toList());
    return QName.of(parts);
  }

  @Override
  public Stmt visitCreateContext(SqlStreamParser.CreateContextContext c) {
    Identifier name = visitIdentifier(c.identifier());
    return new CreateContext(new Context(QName.of(name)));
  }

  // -- Type declarations -----------------------------------------------------
  @Override
  public Stmt visitCreateScalar(SqlStreamParser.CreateScalarContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = QName.of(name);
    PrimitiveT pt = visitPrimitiveType(c.primitiveType());
    Optional<Expr> validation = c.expr() != null ? Optional.of((Expr)visitExpr(c.expr())) : Optional.empty();
    Optional<PrimitiveV> def;
    if (c.literalValue() != null) {
      PrimitiveV l = visitLiteralValue(c.literalValue());
      def = Optional.of(l);
    } else {
      def = Optional.empty();
    }
    return new CreateScalar(new ScalarT(qn, pt, validation, def));
  }

  @Override
  public CreateEnum visitCreateEnum(SqlStreamParser.CreateEnumContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = QName.of(name);
    BoolV isMasked = c.MASK() != null ? new BoolV(true) : new BoolV(false);
    IntegerT enumType = (c.enumType() != null) ? visitEnumType(c.enumType()) : Int32T.get();
    List<EnumSymbol> symbols = c.enumSymbol().stream().map(this::visitEnumSymbol).collect(toList());
    Optional<Identifier> def = c.DEFAULT() != null ? Optional.of(visitIdentifier(c.identifier())) : Optional.empty();
    return new CreateEnum(new EnumT(qn, enumType, isMasked, symbols, def));
  }

  @Override
  public EnumSymbol visitEnumSymbol(SqlStreamParser.EnumSymbolContext c) {
    Int64V value = int64(c.NUMBER_LIT());
    return new EnumSymbol(visitIdentifier(c.identifier()), value);
  }

  @Override
  public IntegerT visitEnumType(SqlStreamParser.EnumTypeContext c) {
    if (c.INT8() != null) return Int8T.get();
    if (c.INT16() != null) return Int16T.get();
    if (c.INT32() != null) return Int32T.get();
    if (c.INT64() != null) return Int64T.get();
    return Int32T.get(); // default
  }

  @Override
  public Stmt visitCreateStruct(SqlStreamParser.CreateStructContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = QName.of(name);
    List<Field> fs = c.fieldDef().stream().map(this::visitStructField).collect(toList());
    return new CreateStruct(new StructT(qn, fs));
  }

  @Override
  public Stmt visitCreateUnion(SqlStreamParser.CreateUnionContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = QName.of(name);
    List<UnionAlt> alts = c.unionAlt().stream()
        .map(a -> new UnionAlt(visitIdentifier(a.identifier()), visitDataType(a.dataType())))
        .collect(toList());
    return new CreateUnion(new UnionT(qn, alts));
  }

  private Field visitStructField(SqlStreamParser.FieldDefContext f) {
    // DEFAULT on a field may be any literal (including jsonLiteral). visitLiteral returns AnyV.
    Optional<AnyV> def = Optional.empty();
    if (f.DEFAULT() != null) {
      if (f.literal() == null) {
        throw new IllegalStateException("DEFAULT must be followed by a literal");
      }
      def = Optional.of((AnyV) visit(f.literal()));
    }
    BoolV isOptional = f.OPTIONAL() != null ? new BoolV(true) : new BoolV(false);
    return new Field(visitIdentifier(f.identifier()), visitDataType(f.dataType()), isOptional, def);
  }

  @Override
  public AnyT visitDataType(SqlStreamParser.DataTypeContext d) {
    if (d.primitiveType() != null) return visitPrimitiveType(d.primitiveType());
    if (d.compositeType() != null) return visitCompositeType(d.compositeType());
    if (d.complexType() != null) return visitComplexType(d.complexType());
    throw new IllegalStateException("unknown dataType");
  }

  @Override
  public PrimitiveT visitPrimitiveType(SqlStreamParser.PrimitiveTypeContext p) {
    if (p.BOOL() != null) return BoolT.get();
    if (p.INT8() != null) return Int8T.get();
    if (p.INT16() != null) return Int16T.get();
    if (p.INT32() != null) return Int32T.get();
    if (p.INT64() != null) return Int64T.get();
    if (p.FLOAT32() != null) return Float32T.get();
    if (p.FLOAT64() != null) return Float64T.get();
    if (p.DECIMAL() != null) return DecimalT.get(int8(p.NUMBER_LIT(0)), int8(p.NUMBER_LIT(1)));
    if (p.STRING() != null) return StringT.get();
    if (p.CHAR() != null) return CharT.get(int32(p.NUMBER_LIT(0)));
    if (p.BYTES() != null) return BytesT.get();
    if (p.FIXED() != null) return FixedT.get(int32(p.NUMBER_LIT(0)));
    if (p.UUID() != null) return UuidT.get();
    if (p.DATE() != null) return DateT.get();
    if (p.TIME() != null) return TimeT.get(int8(p.NUMBER_LIT(0)));
    if (p.TIMESTAMP() != null) return TimestampT.get(int8(p.NUMBER_LIT(0)));
    if (p.TIMESTAMP_TZ() != null) return TimestampTzT.get(int8(p.NUMBER_LIT(0)));
    throw new IllegalStateException("unknown primitive type");
  }

  @Override
  public AnyT visitCompositeType(SqlStreamParser.CompositeTypeContext c) {
    if (c.LIST() != null) {
      AnyT item = visitDataType(c.dataType());
      return new ListT(item);
    } else {
      PrimitiveT key = visitPrimitiveType(c.primitiveType());
      AnyT val = visitDataType(c.dataType());
      return new MapT(key, val);
    }
  }

  @Override
  public AnyT visitComplexType(SqlStreamParser.ComplexTypeContext c) {
    QName qn = typeRefQName(c.qname());
    return new TypeRef(qn);
  }

  // -- CREATE STREAM --------------------------------------------------------
  @Override
  public Stmt visitCreateStream(SqlStreamParser.CreateStreamContext c) {
    List<StreamType> alts = c.streamTypeDef().stream().map(std -> {
      List<Identifier> dist = std.distributionClause() == null
          ? List.of()
          : std.distributionClause().identifier().stream().map(this::visitIdentifier).collect(toList());
      if (std.inlineStruct() != null) {
        List<Field> fs = std.inlineStruct().fieldDef().stream().map(this::visitStructField).collect(toList());
        return new StreamInlineT(fs, visitIdentifier(std.typeAlias().identifier()), dist);
      } else {
        return new StreamReferenceT(new TypeRef(typeRefQName(std.qname())), visitIdentifier(std.typeAlias().identifier()), dist);
      }
    }).collect(toList());
    QName qn = QName.of(visitIdentifier(c.identifier()));
    DataStream s = c.LOG() != null ? new StreamLog(qn, alts) : new StreamCompact(qn, alts);
    return new CreateStream(s);
  }

  // -- READ ---------------------------------------------------------------
  @Override
  public Stmt visitReadStmt(SqlStreamParser.ReadStmtContext c) {
    QName stream = typeRefQName(c.streamName().qname());
    List<ReadSelection> blocks = c.typeBlock().stream().map(tb -> {
      Identifier tn = visitIdentifier(tb.typeName().identifier());
      Projection proj = visitReadProjection(tb.readProjection());
      Optional<WhereClause> where = tb.whereClause() != null ? Optional.of(new WhereClause((Expr) visit(tb.whereClause().expr()))) : Optional.empty();
      return new ReadSelection(tn, proj, where);
    }).collect(toList());
    return new ReadStmt(stream, blocks);
  }

  @Override
  public Projection visitReadProjection(SqlStreamParser.ReadProjectionContext ctx) {
    if (ctx.getStart().getType() == SqlStreamParser.STAR) {
      return ProjectionAll.getInstance();
    } else {
      List<ProjectionExpr> items = new ArrayList<>();
      ctx.readProjectionExpr().stream().map(this::visitReadProjectionExpr).forEach(items::add);
      return new ProjectionList(items);
    }
  }

  @Override
  public ProjectionExpr visitReadProjectionExpr(SqlStreamParser.ReadProjectionExprContext ctx) {
    Expr expr = visitExpr(ctx.expr());
    Optional<Identifier> alias = ctx.identifier() != null ? Optional.of(visitIdentifier(ctx.identifier())) : Optional.empty();
    return new ProjectionExpr(expr, alias);
  }


  // -- WRITE --------------------------------------------------------------
  @Override
  public WriteStmt visitWriteStmt(SqlStreamParser.WriteStmtContext c) {
    QName stream = typeRefQName(c.streamName().qname());
    Identifier typeName = visitIdentifier(c.typeName().identifier());
    List<StructV> rows = visitWriteValues(c.writeValues());
    if(rows.isEmpty()) throw new IllegalArgumentException("No VALUES provided");
    return new WriteStmt(stream, typeName, rows);
  }

  @Override
  public List<StructV> visitWriteValues(SqlStreamParser.WriteValuesContext ctx) {
    return ctx.structLiteral().stream().map(this::visitStructLiteral).collect(toList());
  }

  @Override
  public StructV visitStructLiteral(SqlStreamParser.StructLiteralContext ctx) {
    Map<Identifier, AnyV> entries = new LinkedHashMap<>();
    ctx.structEntry().stream().map(this::visitStructEntry).forEach(e -> entries.put(e.getKey(), e.getValue()));
    return new StructV(entries);
  }

  @Override
  public Map.Entry<Identifier, AnyV> visitStructEntry(SqlStreamParser.StructEntryContext ctx) {
    Identifier key = visitIdentifier(ctx.identifier());
    AnyV val = visitLiteral(ctx.literal());
    return Map.entry(key, val);
  }

  @Override
  public MapV visitMapLiteral(SqlStreamParser.MapLiteralContext ctx) {
    Map<PrimitiveV, AnyV> m = new LinkedHashMap<>();
    for (var e : ctx.mapEntry()) {
      PrimitiveV key = visitLiteralValue(e.literalValue());
      AnyV val = visitLiteral(e.literal());
      m.put(key, val);
    }
    return new MapV(m);
  }

  @Override
  public Expr visitExpr(SqlStreamParser.ExprContext ctx) {
    return visitOrExpr(ctx.orExpr());
  }

  @Override
  public Expr visitOrExpr(SqlStreamParser.OrExprContext ctx) {
    List<Expr> exprs = ctx.andExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    return exprs.size() == 1 ? exprs.get(0) : fold(exprs, InfixOp.OR);
  }

  @Override
  public Expr visitAndExpr(SqlStreamParser.AndExprContext ctx) {
    List<Expr> exprs = ctx.notExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    return exprs.size() == 1 ? exprs.get(0) : fold(exprs, InfixOp.AND);
  }

  @Override
  public Expr visitNotExpr(SqlStreamParser.NotExprContext ctx) {
    if (ctx.NOT() != null) {
      Expr inner = (Expr) visit(ctx.notExpr());
      return new PrefixExpr(PrefixOp.NOT, inner, VoidT.get());
    }
    return (Expr) visit(ctx.cmpExpr());
  }

  @Override
  public Expr visitCmpExpr(SqlStreamParser.CmpExprContext ctx) {
    // leftmost shiftExpr (grammar changed from mulExpr -> shiftExpr)
    Expr left = (Expr) visit(ctx.shiftExpr().get(0));
    if (ctx.shiftExpr().size() == 1 && ctx.getChildCount() == 1) return left;

    Expr result = left;
    int shiftIndex = 1;
    for (int i = 1; i < ctx.getChildCount(); i++) {
      var child = ctx.getChild(i);
      String tokenText = child.getText();
      switch (tokenText) {
        case "=":
          result = new InfixExpr(InfixOp.EQ, result, (Expr) visit(ctx.shiftExpr().get(shiftIndex++)), VoidT.get());
          break;
        case "<>":
        case "!=":
          result = new InfixExpr(InfixOp.NEQ, result, (Expr) visit(ctx.shiftExpr().get(shiftIndex++)), VoidT.get());
          break;
        case "<":
          result = new InfixExpr(InfixOp.LT, result, (Expr) visit(ctx.shiftExpr().get(shiftIndex++)), VoidT.get());
          break;
        case "<=":
          result = new InfixExpr(InfixOp.LTE, result, (Expr) visit(ctx.shiftExpr().get(shiftIndex++)), VoidT.get());
          break;
        case ">":
          result = new InfixExpr(InfixOp.GT, result, (Expr) visit(ctx.shiftExpr().get(shiftIndex++)), VoidT.get());
          break;
        case ">=":
          result = new InfixExpr(InfixOp.GTE, result, (Expr) visit(ctx.shiftExpr().get(shiftIndex++)), VoidT.get());
          break;
        default:
          if (child instanceof TerminalNode) {
            String up = tokenText.toUpperCase(Locale.ROOT);
            if (up.equals("IS")) {
              String nxt = ctx.getChild(i + 1).getText().toUpperCase(Locale.ROOT);
              if (nxt.equals("NULL")) {
                result = new InfixExpr(InfixOp.IS_NULL, result, NullV.INSTANCE, VoidT.get());
                i++;
              } else if (nxt.equals("NOT")) {
                String nxt2 = ctx.getChild(i + 2).getText().toUpperCase(Locale.ROOT);
                if (nxt2.equals("NULL")) {
                  result = new InfixExpr(InfixOp.IS_NOT_NULL, result, NullV.INSTANCE, VoidT.get());
                  i += 2;
                } else {
                  throw new IllegalStateException("Unsupported IS expression");
                }
              } else {
                throw new IllegalStateException("Unsupported IS expression");
              }
            } else if (up.equals("BETWEEN")) {
              Expr lower = (Expr) visit(ctx.shiftExpr().get(shiftIndex++));
              Expr upper = (Expr) visit(ctx.shiftExpr().get(shiftIndex++));
              result = new Ternary(TernaryOp.BETWEEN, result, lower, upper, VoidT.get());
            } else if (up.equals("IN")) {
              List<AnyV> lits = ctx.literal().stream().map(l -> (AnyV) visit(l)).collect(toList());
              result = new InfixExpr(InfixOp.IN, result, new ListV(lits), VoidT.get());
            }
          }
          break;
      }
    }
    return result;
  }

  @Override
  public Expr visitAddExpr(SqlStreamParser.AddExprContext ctx) {
    List<Expr> exprs = ctx.mulExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    if (exprs.size() == 1) return exprs.get(0);
    Expr res = exprs.get(0);
    for (int i = 1; i < exprs.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      InfixOp bop = op.equals("+") ? InfixOp.ADD : InfixOp.SUB;
      res = new InfixExpr(bop, res, exprs.get(i), VoidT.get());
    }
    return res;
  }

  @Override
  public Expr visitMulExpr(SqlStreamParser.MulExprContext ctx) {
    List<SqlStreamParser.UnaryExprContext> parts = ctx.unaryExpr();
    Expr result = (Expr) visit(parts.get(0));
    if (parts.size() == 1) return result;

    for (int i = 1; i < parts.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      Expr right = (Expr) visit(parts.get(i));
      switch (op) {
        case "*":
          result = new InfixExpr(InfixOp.MUL, result, right, VoidT.get());
          break;
        case "/":
          result = new InfixExpr(InfixOp.DIV, result, right, VoidT.get());
          break;
        case "%":
          result = new InfixExpr(InfixOp.MOD, result, right, VoidT.get());
          break;
        default:
          throw new IllegalStateException("Unknown multiplicative operator: " + op);
      }
    }
    return result;
  }

  @Override
  public Expr visitShiftExpr(SqlStreamParser.ShiftExprContext ctx) {
    List<Expr> parts = ctx.addExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    if (parts.size() == 1) return parts.get(0);
    Expr res = parts.get(0);
    for (int i = 1; i < parts.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      Expr right = parts.get(i);
      if (op.equals("<<")) {
        res = new InfixExpr(InfixOp.SHL, res, right, VoidT.get());
      } else if (op.equals(">>")) {
        res = new InfixExpr(InfixOp.SHR, res, right, VoidT.get());
      } else {
        throw new IllegalStateException("Unknown shift operator: " + op);
      }
    }
    return res;
  }

  @Override
  public Expr visitUnaryExpr(SqlStreamParser.UnaryExprContext ctx) {
    if (ctx.MINUS() != null) {
      Expr inner = (Expr) visit(ctx.unaryExpr());
      return new PrefixExpr(PrefixOp.NEG, inner, VoidT.get());
    }
    // otherwise it's a postfixExpr
    return (Expr) visit(ctx.postfixExpr());
  }

  // add visitPostfixExpr to build accessor/member/index chains (conservative mapping)
  @Override
  public Expr visitPostfixExpr(SqlStreamParser.PostfixExprContext ctx) {
    Expr node = visitPrimary(ctx.primary());
    if (ctx.getChildCount() == 1)
      return node;

    // We will conservatively map postfix chains to the old Accessor/Segment/IndexAccessor
    // structures when possible (identifier-based chains or literal indexers).
    // If an index expr is non-literal we reject (unsupported here).
    int childCount = ctx.getChildCount();
    // primary is child 0, subsequent children are member/index nodes/terminals
    for (int i = 1; i < childCount; i++) {
      var ch = ctx.getChild(i);
      switch (ch) {
        case SqlStreamParser.MemberAccessContext ma:
          Identifier member = visitIdentifier(ma.identifier());
          node = new MemberExpr(node, member, VoidT.get());
          break;
        case SqlStreamParser.IndexAccessContext ix:
          Expr index = visitExpr(ix.expr());
          node = new IndexExpr(node, index, VoidT.get());
          break;
        default:
          String t = ch.getText();
          if (t.equals("." ) || t.equals("[" ) || t.equals("]"))
            continue;
          throw new IllegalStateException("Unexpected token in postfix expression: " + t);
      }
    }
    return node;
  }

  @Override
  public Expr visitPrimary(SqlStreamParser.PrimaryContext ctx) {
    if (ctx.LPAREN() != null) {
      return (Expr) visit(ctx.expr());
    }
    if (ctx.literal() != null) {
      return (Expr) visit(ctx.literal());
    }
    if (ctx.identifier() != null) {
      return new Symbol(visitIdentifier(ctx.identifier()), VoidT.get());
    }
    throw new IllegalStateException("unknown primary");
  }

  // dispatch between literalValue and literalSeq (new grammar)
  @Override
  public AnyV visitLiteral(SqlStreamParser.LiteralContext c) {
    if (c.NULL() != null) return NullV.INSTANCE;
    if (c.literalValue() != null) return (AnyV) visitLiteralValue(c.literalValue());
    if (c.literalSeq() != null) return (AnyV) visitLiteralSeq(c.literalSeq());
    if (c.structLiteral() != null) return (AnyV) visitStructLiteral(c.structLiteral());
    if (c.enumLiteral() != null) return (AnyV) visitEnumLiteral(c.enumLiteral());
    if (c.unionLiteral() != null) return (AnyV) visitUnionLiteral(c.unionLiteral());
    throw new IllegalStateException("unknown literal");
  }

  @Override
  public PrimitiveV visitLiteralValue(SqlStreamParser.LiteralValueContext c) {
    if (c.STRING_LIT() != null) return string(c.STRING_LIT());
    if (c.NUMBER_LIT() != null) return fractional(c.NUMBER_LIT());
    if (c.TRUE() != null) return new BoolV(true);
    if (c.FALSE() != null) return new BoolV(false);
    throw new IllegalStateException("unknown literalValue");
  }

  @Override
  public EnumV visitEnumLiteral(SqlStreamParser.EnumLiteralContext c) {
    Identifier name = visitIdentifier(c.identifier(0));
    Identifier value = visitIdentifier(c.identifier(1));
    return new EnumV(name, value);
  }

  @Override
  public UnionV visitUnionLiteral(SqlStreamParser.UnionLiteralContext c) {
    Identifier alt = visitIdentifier(c.identifier());
    AnyV value = visitLiteral(c.literal());
    return new UnionV(alt, value);
  }

  // -- Helpers --------------------------------------------------------------
  private static Expr fold(List<Expr> xs, InfixOp op) {
    return xs.stream().skip(1).reduce(xs.get(0), (a, b) -> new InfixExpr(op, a, b, VoidT.get()));
  }

  private QName typeRefQName(SqlStreamParser.QnameContext q) {
    List<Identifier> parts = q.identifier().stream().map(this::visitIdentifier).collect(toList());
    return QName.of(parts);
  }

  private static StringV string(TerminalNode v) {
    String raw = v.getText();
    return new StringV(unquote(raw));
  }

  private static Float64V fractional(TerminalNode v) {
    if (v == null || v.getText().isEmpty())
      throw new IllegalArgumentException("Expected non-empty NUMBER_LIT");
    return new Float64V(Double.parseDouble(v.getText()));
  }

  private static Int64V int64(TerminalNode v) {
    Float64V f = fractional(v);
    if (f.value() % 1 != 0)
      throw new IllegalArgumentException("Expected integer value, got: " + v.getText());
    if(f.value() < Long.MIN_VALUE || f.value() > Long.MAX_VALUE)
      throw new IllegalArgumentException("Expected 64-bit integer value, got: " + v.getText());
    return new Int64V((f.value().longValue()));
  }

  private static Int32V int32(TerminalNode v) {
    Int64V i = int64(v);
    if(i.value() < Integer.MIN_VALUE || i.value() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Expected 32-bit integer value, got: " + v.getText());
    return new Int32V(i.value().intValue());
  }

  private static Int8V int8(TerminalNode v) {
    Int64V i = int64(v);
    if(i.value() < Byte.MIN_VALUE || i.value() > Byte.MAX_VALUE)
      throw new IllegalArgumentException("Expected 8-bit integer value, got: " + v.getText());
    return new Int8V(i.value().byteValue());
  }

  @Override
  public Identifier visitIdentifier(SqlStreamParser.IdentifierContext id) {
    return new Identifier(id.ID().getText());
  }

  private static String unquote(String s) {
    // 'foo''bar' -> foo'bar  (single-quoted in grammar)
    return s.substring(1, s.length() - 1).replace("''", "'");
  }
}
