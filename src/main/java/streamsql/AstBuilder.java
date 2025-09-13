package streamsql;

import streamsql.ast.*;
import streamsql.ast.Enum;
import streamsql.parse.SqlStreamParser;
import streamsql.parse.SqlStreamParserBaseVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

import static java.util.stream.Collectors.toList;

public class AstBuilder extends SqlStreamParserBaseVisitor<Object> {

  private QName currentContext = QName.root();

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
    QName qn = contextQName(currentContext, c.qname());
    currentContext = qn;
    return new UseContext(new Context(qn));
  }

  @Override
  public Stmt visitCreateContext(SqlStreamParser.CreateContextContext c) {
    Identifier name = visitIdentifier(c.identifier());
    QName qn = currentContext.append(name);
    return new CreateContext(new Context(qn));
  }

  // -- Type declarations -----------------------------------------------------
  @Override
  public Stmt visitCreateScalar(SqlStreamParser.CreateScalarContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = currentContext.append(name);
    PrimitiveType pt = visitPrimitiveType(c.primitiveType());
    Optional<Expr> validation = c.expr() != null ? Optional.of((Expr)visitExpr(c.expr())) : Optional.empty();
    Optional<Literal> def = c.literal() != null ? Optional.of((Literal)visitLiteral(c.literal())) : Optional.empty();
    return new CreateScalar(new Scalar(qn, pt, validation, def));
  }

  @Override
  public CreateEnum visitCreateEnum(SqlStreamParser.CreateEnumContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = currentContext.append(name);
    BoolV isMasked = c.MASK() != null ? new BoolV(true) : new BoolV(false);
    IntegerT enumType = (c.enumType() != null) ? visitEnumType(c.enumType()) : Int32T.get();
    List<EnumSymbol> symbols = c.enumSymbol().stream().map(this::visitEnumSymbol).collect(toList());
    Optional<Identifier> def = c.DEFAULT() != null ? Optional.of(visitIdentifier(c.identifier())) : Optional.empty();
    return new CreateEnum(new Enum(qn, enumType, isMasked, symbols, def));
  }

  @Override
  public EnumSymbol visitEnumSymbol(SqlStreamParser.EnumSymbolContext c) {
    Int64V value = integer(c.INTEGER_LIT());
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
    QName qn = currentContext.append(name);
    List<Field> fs = c.fieldDef().stream().map(this::visitStructField).collect(toList());
    return new CreateStruct(new Struct(qn, fs));
  }

  @Override
  public Stmt visitCreateUnion(SqlStreamParser.CreateUnionContext c) {
    Identifier name = visitIdentifier(c.typeName().identifier());
    QName qn = currentContext.append(name);
    List<UnionAlt> alts = c.unionAlt().stream()
        .map(a -> new UnionAlt(visitIdentifier(a.identifier()), visitDataType(a.dataType())))
        .collect(toList());
    return new CreateUnion(new Union(qn, alts));
  }

  private Field visitStructField(SqlStreamParser.FieldDefContext f) {
    Optional<StringV> def = f.jsonString() != null ? Optional.of(string(f.jsonString().STRING_LIT())) : Optional.empty();
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
  public PrimitiveType visitPrimitiveType(SqlStreamParser.PrimitiveTypeContext p) {
    if (p.BOOL() != null) return BoolT.get();
    if (p.INT8() != null) return Int8T.get();
    if (p.INT16() != null) return Int16T.get();
    if (p.INT32() != null) return Int32T.get();
    if (p.INT64() != null) return Int64T.get();
    if (p.FLOAT32() != null) return Float32T.get();
    if (p.FLOAT64() != null) return Float64T.get();
    if (p.DECIMAL() != null) return DecimalT.get(int8FromIntegerLit(p.INTEGER_LIT(0)), int8FromIntegerLit(p.INTEGER_LIT(1)));
    if (p.STRING() != null) return StringT.get();
    if (p.CHAR() != null) return CharT.get(int32FromIntegerLit(p.INTEGER_LIT(0)));
    if (p.BYTES() != null) return BytesT.get();
    if (p.FIXED() != null) return FixedT.get(int32FromIntegerLit(p.INTEGER_LIT(0)));
    if (p.UUID() != null) return UuidT.get();
    if (p.DATE() != null) return DateT.get();
    if (p.TIME() != null) return TimeT.get(int8FromIntegerLit(p.INTEGER_LIT(0)));
    if (p.TIMESTAMP() != null) return TimestampT.get(int8FromIntegerLit(p.INTEGER_LIT(0)));
    if (p.TIMESTAMP_TZ() != null) return TimestampTzT.get(int8FromIntegerLit(p.INTEGER_LIT(0)));
    throw new IllegalStateException("unknown primitive type");
  }

  private static Int32V int32FromIntegerLit(TerminalNode v) {
    if (v == null) return new Int32V(0);
    return new Int32V(Integer.parseInt(v.getText()));
  }

  private static Int8V int8FromIntegerLit(TerminalNode v) {
    if (v == null) return new Int8V((byte) 0);
    return new Int8V(Byte.parseByte(v.getText()));
  }

  @Override
  public AnyT visitCompositeType(SqlStreamParser.CompositeTypeContext c) {
    if (c.LIST() != null) {
      AnyT item = visitDataType(c.dataType());
      return new ListT(item);
    } else {
      PrimitiveType key = visitPrimitiveType(c.primitiveType());
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
    QName qn = currentContext.append(visitIdentifier(c.identifier()));
    DataStream s = c.LOG() != null ? new StreamLog(qn, alts) : new StreamCompact(qn, alts);
    return new CreateStream(s);
  }

  // -- READ ---------------------------------------------------------------
  @Override
  public Stmt visitReadStmt(SqlStreamParser.ReadStmtContext c) {
    QName stream = typeRefQName(c.streamName().qname());
    List<ReadSelection> blocks = c.typeBlock().stream().map(tb -> {
      Identifier tn = visitIdentifier(tb.typeName().identifier());
      Projection proj;
      if (tb.projection().getStart().getType() == SqlStreamParser.STAR) {
        proj = ProjectionAll.getInstance();
      } else {
        List<Accessor> items = tb.projection().accessor().stream().map(this::visitAccessor).collect(toList());
        proj = new ProjectionList(items);
      }
      Optional<WhereClause> where = tb.whereClause() != null ? Optional.of(new WhereClause((Expr) visit(tb.whereClause().expr()))) : Optional.empty();
      return new ReadSelection(tn, proj, where);
    }).collect(toList());
    return new ReadStmt(stream, blocks);
  }

  // -- WRITE --------------------------------------------------------------
  @Override
  public Stmt visitWriteStmt(SqlStreamParser.WriteStmtContext c) {
    QName stream = typeRefQName(c.streamName().qname());
    Identifier typeName = visitIdentifier(c.typeName().identifier());

    Projection proj;
    if (c.projection().getStart().getType() == SqlStreamParser.STAR) {
      proj = ProjectionAll.getInstance();
    } else {
      List<Accessor> items = c.projection().accessor().stream().map(this::visitAccessor).collect(toList());
      proj = new ProjectionList(items);
    }

    List<ListV> rows = c.tuple().stream().map(t -> {
      List<AnyV> values = t.literal().stream().map(l -> (AnyV) visit(l)).collect(toList());
      return new ListV(values);
    }).collect(toList());

    var counts = rows.stream().map(row -> row.values().size()).distinct().count();
    if(counts == 0) throw new IllegalArgumentException("No VALUES provided");
    if(counts > 1) throw new IllegalArgumentException("VALUES arity mismatch");

    return new WriteStmt(stream, typeName, proj, rows);
  }

  @Override
  public Accessor visitAccessor(SqlStreamParser.AccessorContext ctx) {
    // identifier form
    if (ctx.identifier() != null) {
      Identifier head = visitIdentifier(ctx.identifier());
      if (ctx.accessor() == null) return head;

      Accessor tail = visitAccessor(ctx.accessor());
      // recursive Segment: head + tail (tail itself may be Identifier, Indexer or Segment)
      return new Segment(head, tail);
    }

    // indexer form: '[' literal ']' (DOT accessor)?
    if (ctx.literal() != null) {
      Literal lit = (Literal) visit(ctx.literal());
      Indexer head = new Indexer(lit);
      if (ctx.accessor() == null) return head;

      Accessor tail = visitAccessor(ctx.accessor());
      return new Segment(head, tail);
    }

    throw new IllegalStateException("Unknown accessor form");
  }

  @Override
  public Expr visitOrExpr(SqlStreamParser.OrExprContext ctx) {
    List<Expr> exprs = ctx.andExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    return exprs.size() == 1 ? exprs.get(0) : fold(exprs, BinaryOp.OR);
  }

  @Override
  public Expr visitAndExpr(SqlStreamParser.AndExprContext ctx) {
    List<Expr> exprs = ctx.notExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    return exprs.size() == 1 ? exprs.get(0) : fold(exprs, BinaryOp.AND);
  }

  @Override
  public Expr visitNotExpr(SqlStreamParser.NotExprContext ctx) {
    if (ctx.NOT() != null) {
      Expr inner = (Expr) visit(ctx.notExpr());
      return new Unary(UnaryOp.NOT, inner);
    }
    return (Expr) visit(ctx.cmpExpr());
  }

  @Override
  public Expr visitCmpExpr(SqlStreamParser.CmpExprContext ctx) {
    // leftmost addExpr
    Expr left = (Expr) visit(ctx.addExpr(0));
    if (ctx.addExpr().size() == 1 && ctx.getChildCount() == 1) return left;

    Expr result = left;
    int addIndex = 1;
    for (int i = 1; i < ctx.getChildCount(); i++) {
      var child = ctx.getChild(i);
      String tokenText = child.getText();
      switch (tokenText.toUpperCase(Locale.ROOT)) {
        case "=":
          result = new Binary(BinaryOp.EQ, result, (Expr) visit(ctx.addExpr(addIndex++)));
          break;
        case "<>":
        case "!=":
          result = new Binary(BinaryOp.NEQ, result, (Expr) visit(ctx.addExpr(addIndex++)));
          break;
        case "<":
          result = new Binary(BinaryOp.LT, result, (Expr) visit(ctx.addExpr(addIndex++)));
          break;
        case "<=":
          result = new Binary(BinaryOp.LTE, result, (Expr) visit(ctx.addExpr(addIndex++)));
          break;
        case ">":
          result = new Binary(BinaryOp.GT, result, (Expr) visit(ctx.addExpr(addIndex++)));
          break;
        case ">=":
          result = new Binary(BinaryOp.GTE, result, (Expr) visit(ctx.addExpr(addIndex++)));
          break;
        default:
          if (child instanceof TerminalNode) {
            String up = tokenText.toUpperCase(Locale.ROOT);
            if (up.equals("IS")) {
              String nxt = ctx.getChild(i + 1).getText().toUpperCase(Locale.ROOT);
              if (nxt.equals("NULL")) {
                result = new Binary(BinaryOp.IS_NULL, result, NullV.INSTANCE);
                i++;
              } else if (nxt.equals("NOT")) {
                String nxt2 = ctx.getChild(i + 2).getText().toUpperCase(Locale.ROOT);
                if (nxt2.equals("NULL")) {
                  result = new Binary(BinaryOp.IS_NOT_NULL, result, NullV.INSTANCE);
                  i += 2;
                } else {
                  throw new IllegalStateException("Unsupported IS expression");
                }
              } else {
                throw new IllegalStateException("Unsupported IS expression");
              }
            } else if (up.equals("BETWEEN")) {
              Expr lower = (Expr) visit(ctx.addExpr(addIndex++));
              Expr upper = (Expr) visit(ctx.addExpr(addIndex++));
              result = new Terniary(TerniaryOp.BETWEEN, result, lower, upper);
            } else if (up.equals("IN")) {
              List<AnyV> lits = ctx.literal().stream().map(l -> (AnyV) visit(l)).collect(toList());
              result = new Binary(BinaryOp.IN, result, new ListV(lits));
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
      BinaryOp bop = op.equals("+") ? BinaryOp.ADD : BinaryOp.SUB;
      res = new Binary(bop, res, exprs.get(i));
    }
    return res;
  }

  @Override
  public Expr visitMulExpr(SqlStreamParser.MulExprContext ctx) {
    List<Expr> exprs = ctx.unaryExpr().stream().map(e -> (Expr) visit(e)).collect(toList());
    if (exprs.size() == 1) return exprs.get(0);
    Expr res = exprs.get(0);
    for (int i = 1; i < exprs.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      BinaryOp bop = switch (op) {
        case "*" -> BinaryOp.MUL;
        case "/" -> BinaryOp.DIV;
        case "%" -> BinaryOp.MOD;
        default -> throw new IllegalStateException("Unknown mul operator: " + op);
      };
      res = new Binary(bop, res, exprs.get(i));
    }
    return res;
  }

  @Override
  public Expr visitUnaryExpr(SqlStreamParser.UnaryExprContext ctx) {
    if (ctx.MINUS() != null) {
      Expr inner = (Expr) visit(ctx.unaryExpr());
      return new Unary(UnaryOp.NEG, inner);
    }
    if (ctx.expr() != null) {
      return (Expr) visit(ctx.expr());
    }
    if (ctx.literal() != null) {
      return (Expr) visit(ctx.literal());
    }
    if (ctx.accessor() != null) {
      // Accessor implements Accessor and therefore Expr
      return visitAccessor(ctx.accessor());
    }
    throw new IllegalStateException("Unknown unaryExpr");
  }

  @Override
  public Literal visitLiteral(SqlStreamParser.LiteralContext c) {
    if (c.NULL() != null) return NullV.INSTANCE;
    if (c.TRUE() != null) return new BoolV(true);
    if (c.FALSE() != null) return new BoolV(false);
    if (c.STRING_LIT() != null) return string(c.STRING_LIT());
    if (c.INTEGER_LIT() != null) return integer(c.INTEGER_LIT());
    if (c.NUMBER_LIT() != null) return fractional(c.NUMBER_LIT());
    throw new IllegalStateException("unknown literal");
  }

  // -- Helpers --------------------------------------------------------------
  private static Expr fold(List<Expr> xs, BinaryOp op) {
    return xs.stream().skip(1).reduce(xs.get(0), (a, b) -> new Binary(op, a, b));
  }

  private QName contextQName(QName cc, SqlStreamParser.QnameContext q) {
    List<Identifier> parts = q.identifier().stream().map(this::visitIdentifier).collect(toList());
    return QName.join(cc, QName.of(parts));
  }

  private QName typeRefQName(SqlStreamParser.QnameContext q) {
    List<Identifier> parts = q.identifier().stream().map(this::visitIdentifier).collect(toList());
    return QName.of(parts);
  }

  private static Int64V integer(TerminalNode v) {
    return new Int64V(Long.parseLong(v.getText()));
  }

  private static Float64V fractional(TerminalNode v) {
    return new Float64V(Double.parseDouble(v.getText()));
  }

  private static StringV string(TerminalNode v) {
    String raw = v.getText();
    return new StringV(unquote(raw));
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
