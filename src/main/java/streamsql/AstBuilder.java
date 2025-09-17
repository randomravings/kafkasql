package streamsql;

import streamsql.ast.*;
import streamsql.parse.SqlStreamParser;
import streamsql.parse.SqlStreamParserBaseVisitor;
import streamsql.parse.SqlStreamParser.ReadProjectionExprContext;
import streamsql.parse.SqlStreamParser.ReadStmtContext;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.ibm.icu.impl.locale.LocaleValidityChecker.Where;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public class AstBuilder extends SqlStreamParserBaseVisitor<AstNode> {

  @Override
  public Ast visitScript(SqlStreamParser.ScriptContext ctx) {
    var range = range(ctx);
    var statements = ctx.statement().stream()
        .map(s -> (Stmt) visit(s))
        .collect(toList());
    return new Ast(range, statements);
  }

  @Override
  public Stmt visitStatement(SqlStreamParser.StatementContext ctx) {
    if (ctx.useStmt() != null) return (Stmt) visit(ctx.useStmt());
    if (ctx.createStmt() != null) return visitCreateStmt(ctx.createStmt());
    if (ctx.readStmt() != null) return (Stmt) visitReadStmt(ctx.readStmt());
    if (ctx.writeStmt() != null) return (Stmt) visitWriteStmt(ctx.writeStmt());
    throw new IllegalStateException("unknown statement");
  }

  @Override
  public UseStmt visitUseStmt(SqlStreamParser.UseStmtContext c) {
    if (c.useContext() != null) return visitUseContext(c.useContext());
    throw new IllegalStateException("unknown use statement");
  }

  @Override
  public UseContext visitUseContext(SqlStreamParser.UseContextContext c) {
    Range range = range(c);
    QName qn = visitQname(c.qname());
    return new UseContext(range, qn);
  }

  @Override
  public CreateStmt visitCreateStmt(SqlStreamParser.CreateStmtContext c) {
    Range range = range(c);
    if (c.objDef().context() != null) {
      Context context = visitContext(c.objDef().context());
      return new CreateContext(range, context);

    }
    if (c.objDef().complexType() != null) {
      ComplexT complexT = visitComplexType(c.objDef().complexType());
      return new CreateType(range, complexT);
    }
    if (c.objDef().stream() != null) {
      StreamT streamT = visitStream(c.objDef().stream());
      return new CreateStream(range, streamT);
    }
    throw new IllegalStateException("unknown create statement");
  }

  @Override
  public Context visitContext(SqlStreamParser.ContextContext c) {
    Range range = range(c);
    QName qn = visitQname(c.qname());
    return new Context(range, qn);
  }

  @Override
  public AnyT visitType(SqlStreamParser.TypeContext c) {
    if (c.primitiveType() != null) return visitPrimitiveType(c.primitiveType());
    if (c.compositeType() != null) return visitCompositeType(c.compositeType());
    if (c.complexType() != null) return visitComplexType(c.complexType());
    if (c.typeReference() != null) return visitTypeReference(c.typeReference());
    throw new IllegalStateException("unknown type");
  }

  @Override
  public PrimitiveT visitPrimitiveType(SqlStreamParser.PrimitiveTypeContext p) {
    if (p.booleanType() != null) return visitBooleanType(p.booleanType());
    if (p.int8Type() != null) return visitInt8Type(p.int8Type());
    if (p.int16Type() != null) return visitInt16Type(p.int16Type());
    if (p.int32Type() != null) return visitInt32Type(p.int32Type());
    if (p.int64Type() != null) return visitInt64Type(p.int64Type());
    if (p.float32Type() != null) return visitFloat32Type(p.float32Type());
    if (p.float64Type() != null) return visitFloat64Type(p.float64Type());
    if (p.decimalType() != null) return visitDecimalType(p.decimalType());
    if (p.stringType() != null) return visitStringType(p.stringType());
    if (p.charType() != null) return visitCharType(p.charType());
    if (p.bytesType() != null) return visitBytesType(p.bytesType());
    if (p.fixedType() != null) return visitFixedType(p.fixedType());
    if (p.uuidType() != null) return visitUuidType(p.uuidType());
    if (p.dateType() != null) return visitDateType(p.dateType());
    if (p.timeType() != null) return visitTimeType(p.timeType());
    if (p.timestampType() != null) return visitTimestampType(p.timestampType());
    if (p.timestampTzType() != null) return visitTimestampTzType(p.timestampTzType());
    throw new IllegalStateException("unknown primitive type");
  }

  @Override
  public BoolT visitBooleanType(SqlStreamParser.BooleanTypeContext c) {
    return new BoolT(range(c));
  }

  @Override
  public Int8T visitInt8Type(SqlStreamParser.Int8TypeContext c) {
    return new Int8T(range(c));
  }

  @Override
  public Int16T visitInt16Type(SqlStreamParser.Int16TypeContext c) {
    return new Int16T(range(c));
  }

  @Override
  public Int32T visitInt32Type(SqlStreamParser.Int32TypeContext c) {
    return new Int32T(range(c));
  }

  @Override
  public Int64T visitInt64Type(SqlStreamParser.Int64TypeContext c) {
    return new Int64T(range(c));
  }

  @Override
  public Float32T visitFloat32Type(SqlStreamParser.Float32TypeContext c) {
    return new Float32T(range(c));
  }

  @Override
  public Float64T visitFloat64Type(SqlStreamParser.Float64TypeContext c) {
    return new Float64T(range(c));
  }

  @Override
  public StringT visitStringType(SqlStreamParser.StringTypeContext c) {
    return new StringT(range(c));
  }

  @Override
  public CharT visitCharType(SqlStreamParser.CharTypeContext c) {
    Range range = range(c);
    int size = int32(c.NUMBER_LIT());
    return new CharT(range, size);
  }

  @Override
  public BytesT visitBytesType(SqlStreamParser.BytesTypeContext c) {
    return new BytesT(range(c));
  }

  @Override
  public FixedT visitFixedType(SqlStreamParser.FixedTypeContext c) {
    Range range = range(c);
    int size = int32(c.NUMBER_LIT());
    return new FixedT(range, size);
  }

  @Override
  public UuidT visitUuidType(SqlStreamParser.UuidTypeContext c) {
    return new UuidT(range(c));
  }

  @Override
  public DateT visitDateType(SqlStreamParser.DateTypeContext c) {
    return new DateT(range(c));
  }

  @Override
  public TimeT visitTimeType(SqlStreamParser.TimeTypeContext c) {
    Range range = range(c);
    byte precision = int8(c.NUMBER_LIT());
    return new TimeT(range, precision);
  }

  @Override
  public TimestampT visitTimestampType(SqlStreamParser.TimestampTypeContext c) {
    Range range = range(c);
    byte precision = int8(c.NUMBER_LIT());
    return new TimestampT(range, precision);
  }

  @Override
  public TimestampTzT visitTimestampTzType(SqlStreamParser.TimestampTzTypeContext c) {
    Range range = range(c);
    byte precision = int8(c.NUMBER_LIT());
    return new TimestampTzT(range, precision);
  }

  @Override
  public DecimalT visitDecimalType(SqlStreamParser.DecimalTypeContext c) {
    Range range = range(c);
    byte precision = int8(c.NUMBER_LIT(0));
    byte scale = int8(c.NUMBER_LIT(1));
    return new DecimalT(range, precision, scale);
  }

  @Override
  public AnyT visitCompositeType(SqlStreamParser.CompositeTypeContext c) {
    if (c.listType() != null) return visitListType(c.listType());
    if (c.mapType() != null) return visitMapType(c.mapType());
    throw new IllegalStateException("unknown composite type");
  }

  @Override
  public ListT visitListType(SqlStreamParser.ListTypeContext c) {
    Range range = range(c);
    AnyT item = visitType(c.type());
    return new ListT(range, item);
  }

  @Override
  public MapT visitMapType(SqlStreamParser.MapTypeContext c) {
    Range range = range(c);
    PrimitiveT key = visitPrimitiveType(c.primitiveType());
    AnyT val = visitType(c.type());
    return new MapT(range, key, val);
  }

  @Override
  public ComplexT visitComplexType(SqlStreamParser.ComplexTypeContext c) {
    if (c.scalarType() != null) return visitScalarType(c.scalarType());
    if (c.structType() != null) return visitStructType(c.structType());
    if (c.enumType() != null) return visitEnumType(c.enumType());
    if (c.unionType() != null) return visitUnionType(c.unionType());
    throw new IllegalStateException("unknown complex type");
  }

  @Override
  public ScalarT visitScalarType(SqlStreamParser.ScalarTypeContext c) {
    Range range = range(c);
    QName qn = visitQname(c.qname());
    PrimitiveT pt = visitPrimitiveType(c.primitiveType());
    AstOptionalNode<Expr> validation = c.expr() != null ? AstOptionalNode.of((Expr)visitExpr(c.expr())) : AstOptionalNode.empty();
    AstOptionalNode<PrimitiveV> def;
    if (c.literalValue() != null) {
      PrimitiveV l = visitLiteralValue(c.literalValue());
      def = AstOptionalNode.of(l);
    } else {
      def = AstOptionalNode.empty();
    }
    return new ScalarT(range, qn, pt, validation, def);
  }

  @Override
  public EnumT visitEnumType(SqlStreamParser.EnumTypeContext c) {
    Range range = range(c);
    QName name = visitQname(c.qname());
    AstOptionalNode<IntegerT> type = c.enumBaseType() != null ? AstOptionalNode.of(visitEnumBaseType(c.enumBaseType())) : AstOptionalNode.empty();
    EnumSymbolList symbols = listContext(c.enumSymbol(), this::visitEnumSymbol, EnumSymbolList::new);
    AstOptionalNode<Identifier> defaultValue = c.DEFAULT() != null ? AstOptionalNode.of(visitEnumSymbolName(c.enumSymbolName())) : AstOptionalNode.empty();
    return new EnumT(range, name, type, symbols, defaultValue);
  }

  @Override
  public IntegerT visitEnumBaseType(SqlStreamParser.EnumBaseTypeContext c) {
    Range range = range(c);
    if (c.INT8() != null) return new Int8T(range);
    if (c.INT16() != null) return new Int16T(range);
    if (c.INT32() != null) return new Int32T(range);
    if (c.INT64() != null) return new Int64T(range);
    throw new IllegalStateException("unknown enum type");
  }

  @Override
  public EnumSymbol visitEnumSymbol(SqlStreamParser.EnumSymbolContext c) {
    Range range = range(c);
    Identifier name = visitEnumSymbolName(c.enumSymbolName());
    IntegerV value = visitEnumSymbolValue(c.enumSymbolValue());
    return new EnumSymbol(range, name, value);
  }

  @Override
  public Identifier visitEnumSymbolName(SqlStreamParser.EnumSymbolNameContext c) {
    return visitIdentifier(c.identifier());
  }

  @Override
  public IntegerV visitEnumSymbolValue(SqlStreamParser.EnumSymbolValueContext c) {
    Range range = range(c);
    long value = int64(c.NUMBER_LIT());
    return new Int64V(range, value);
  }

  @Override
  public StructT visitStructType(SqlStreamParser.StructTypeContext c) {
    Range range = range(c);
    QName name = visitQname(c.qname());
    AstListNode<Field> fs = visitFieldList(c.fieldList());
    return new StructT(range, name, fs);
  }

  @Override
  public FieldList visitFieldList(SqlStreamParser.FieldListContext c) {
    return listContext(c.field(), this::visitField, FieldList::new);
  }

  @Override
  public Field visitField(SqlStreamParser.FieldContext c) {
    Range range = range(c);
    Identifier name = visitFieldName(c.fieldName());
    AnyT type = visitFieldType(c.fieldType());
    AstOptionalNode<NullV> nullable = AstOptionalNode.empty();
    AstOptionalNode<AnyV> defaultValue = AstOptionalNode.empty();
    if (c.fieldNullable() != null) {
      NullV n = visitFieldNullable(c.fieldNullable());
      nullable = AstOptionalNode.of(n);
    }
    if (c.fieldDefaultValue() != null) {
      AnyV l = visitFieldDefaultValue(c.fieldDefaultValue());
      defaultValue = AstOptionalNode.of(l);
    }
    return new Field(range, name, type, nullable, defaultValue);
  }

  @Override
  public Identifier visitFieldName(SqlStreamParser.FieldNameContext c) {
    return visitIdentifier(c.identifier());
  }

  @Override
  public AnyT visitFieldType(SqlStreamParser.FieldTypeContext c) {
    return visitType(c.type());
  }

  @Override
  public NullV visitFieldNullable(SqlStreamParser.FieldNullableContext c) {
    Range range = range(c);
    if (c.NULL() != null) return new NullV(range);
    throw new IllegalStateException("unknown field nullable");
  }

  @Override
  public AnyV visitFieldDefaultValue(SqlStreamParser.FieldDefaultValueContext c) {
    return visitLiteral(c.literal());
  }

  @Override
  public UnionT visitUnionType(SqlStreamParser.UnionTypeContext c) {
    Range range = range(c);
    QName name = visitQname(c.qname());
    UnionMemberList alts = listContext(c.unionMember(), this::visitUnionMember, UnionMemberList::new);
    return new UnionT(range, name, alts);
  }

  @Override
  public UnionMember visitUnionMember(SqlStreamParser.UnionMemberContext c) {
    Range range = range(c);
    Identifier name = visitUnionMemberName(c.unionMemberName());
    AnyT type = visitUnionMemberType(c.unionMemberType());
    return new UnionMember(range, name, type);
  }

  @Override
  public Identifier visitUnionMemberName(SqlStreamParser.UnionMemberNameContext c) {
    return visitIdentifier(c.identifier());
  }

  @Override
  public AnyT visitUnionMemberType(SqlStreamParser.UnionMemberTypeContext c) {
    return visitType(c.type());
  }

  @Override
  public StreamT visitStream(SqlStreamParser.StreamContext c) {
    Range range = range(c);
    QName name = visitQname(c.qname());
    AstListNode<StreamType> types = visitStreamTypeList(c.streamTypeList());
    return new StreamT(range, name, types);
  }

  @Override
  public StreamTypeList visitStreamTypeList(SqlStreamParser.StreamTypeListContext c) {
    return listContext(c.streamType(), this::visitStreamType, StreamTypeList::new);
  }

  @Override
  public StreamType visitStreamType(SqlStreamParser.StreamTypeContext c) {
    Range range = range(c);
    Identifier streamTypeName = visitStreamTypeName(c.streamTypeName());
    AstOptionalNode<DistributeClause> distributeClause = AstOptionalNode.empty();
    if (c.distributeClause() != null) {
      var dc = visitDistributeClause(c.distributeClause());
      distributeClause = AstOptionalNode.of(dc);
    }
    if( c.streamTypeReference() != null) {
      TypeReference typeReference = visitStreamTypeReference(c.streamTypeReference());
      return new StreamReferenceT(range, streamTypeName, typeReference, distributeClause);
    }
    if( c.streamTypeInline() != null) {
      AstListNode<Field> fieldList = visitStreamTypeInline(c.streamTypeInline());
      return new StreamInlineT(range, streamTypeName, fieldList, distributeClause);
    }
    throw new IllegalStateException("unknown stream type");
  }

  public Identifier visitStreamTypeName(SqlStreamParser.StreamTypeNameContext c) {
    return visitIdentifier(c.identifier());
  }

  public DistributeClause visitDistributeClause(SqlStreamParser.DistributeClauseContext c) {
    Range range = range(c);
    IdentifierList keys = listContext(c.fieldName(), this::visitFieldName, IdentifierList::new);
    return new DistributeClause(range, keys);
  }

  @Override
  public TypeReference visitStreamTypeReference(SqlStreamParser.StreamTypeReferenceContext c) {
    return visitTypeReference(c.typeReference());
  }

  @Override
  public TypeReference visitTypeReference(SqlStreamParser.TypeReferenceContext c) {
    Range range = range(c);
    QName qn = visitQname(c.qname());
    return new TypeReference(range, qn);
  }

  @Override
  public AstListNode<Field> visitStreamTypeInline(SqlStreamParser.StreamTypeInlineContext c) {
    return visitFieldList(c.fieldList());
  }

  @Override
  public ReadStmt visitReadStmt(ReadStmtContext ctx) {
    Range range = range(ctx);
    QName streamName = visitStreamName(ctx.streamName());
    ReadTypeBlockList selections = listContext(ctx.readTypeBlock(), this::visitReadTypeBlock, ReadTypeBlockList::new);
    return new ReadStmt(range, streamName, selections);
  }

  @Override
  public ReadTypeBlock visitReadTypeBlock(SqlStreamParser.ReadTypeBlockContext c) {
    Range range = range(c);
    Identifier alias = visitIdentifier(c.identifier());
    Projection projection = visitReadProjection(c.readProjection());
    AstOptionalNode<WhereClause> where = AstOptionalNode.empty();
    if (c.whereClause() != null)
      where = visitWhereClause(c.whereClause());
    return new ReadTypeBlock(range, alias, projection, where);
  }

  @Override
  public Projection visitReadProjection(SqlStreamParser.ReadProjectionContext c) {
    Range range = range(c);
    if (c.STAR() != null)
      return new ProjectionAll(range);
    return listContext(c.readProjectionExpr(), this::visitReadProjectionExpr, ProjectionList::new);
  }

  @Override
  public ProjectionExpr visitReadProjectionExpr(SqlStreamParser.ReadProjectionExprContext c) {
    Range range = range(c);
    Expr e = visitExpr(c.expr());
    AstOptionalNode<Identifier> alias = AstOptionalNode.empty();
    if (c.identifier() != null) {
      Identifier a = visitIdentifier(c.identifier());
      alias = AstOptionalNode.of(a);
    }
    return new ProjectionExpr(range, e, alias);
  }

  @Override
  public AstOptionalNode<WhereClause> visitWhereClause(SqlStreamParser.WhereClauseContext c) {
    Range range = range(c);
    Expr condition = visitExpr(c.expr());
    return AstOptionalNode.of(new WhereClause(range, condition));
  }

  @Override
  public QName visitStreamName(SqlStreamParser.StreamNameContext c) {
    return visitQname(c.qname());
  }

  @Override
  public Expr visitExpr(SqlStreamParser.ExprContext ctx) {
    return visitOrExpr(ctx.orExpr());
  }

  @Override
  public Expr visitOrExpr(SqlStreamParser.OrExprContext ctx) {
    ExprList exprs = listContext(ctx.andExpr(), this::visitAndExpr, ExprList::new);
    return exprs.size() == 1 ? exprs.get(0) : fold(exprs, InfixOp.OR);
  }

  @Override
  public Expr visitAndExpr(SqlStreamParser.AndExprContext ctx) {
    ExprList exprs = listContext(ctx.notExpr(), this::visitNotExpr, ExprList::new);
    return exprs.size() == 1 ? exprs.get(0) : fold(exprs, InfixOp.AND);
  }

  @Override
  public Expr visitNotExpr(SqlStreamParser.NotExprContext ctx) {
    if (ctx.NOT() != null) {
      Range range = range(ctx);
      Expr inner = (Expr) visitNotExpr(ctx.notExpr());
      return new PrefixExpr(range, PrefixOp.NOT, inner, new VoidT(range));
    } else {
      return (Expr) visit(ctx.cmpExpr());
    }
  }

  @Override
  public Expr visitCmpExpr(SqlStreamParser.CmpExprContext ctx) {
    Expr left = (Expr) visit(ctx.shiftExpr().get(0));
    if (ctx.shiftExpr().size() == 1 && ctx.getChildCount() == 1) return left;

    Expr result = left;
    int shiftIndex = 1;
    for (int i = 1; i < ctx.getChildCount(); i++) {
      var child = ctx.getChild(i);
      String tokenText = child.getText();
      switch (tokenText) {
        case "=": {
          Expr right = visitShiftExpr(ctx.shiftExpr().get(shiftIndex++));
          result = mkInfix(InfixOp.EQ, result, right);
          break;
        }
        case "<>": {
          Expr right = visitShiftExpr(ctx.shiftExpr().get(shiftIndex++));
          result = mkInfix(InfixOp.NEQ, result, right);
          break;
        }
        case "<": {
          Expr right = visitShiftExpr(ctx.shiftExpr().get(shiftIndex++));
          result = mkInfix(InfixOp.LT, result, right);
          break;
        }
        case "<=": {
          Expr right = visitShiftExpr(ctx.shiftExpr().get(shiftIndex++));
          result = mkInfix(InfixOp.LTE, result, right);
          break;
        }
        case ">": {
          Expr right = visitShiftExpr(ctx.shiftExpr().get(shiftIndex++));
          result = mkInfix(InfixOp.GT, result, right);
          break;
        }
        case ">=": {
          Expr right = visitShiftExpr(ctx.shiftExpr().get(shiftIndex++));
          result = mkInfix(InfixOp.GTE, result, right);
          break;
        }
        default:
          if (child instanceof TerminalNode) {
            String up = tokenText.toUpperCase(Locale.ROOT);
            if (up.equals("IS")) {
              String nxt = ctx.getChild(i + 1).getText().toUpperCase(Locale.ROOT);
              if (nxt.equals("NULL")) {
                // IS NULL -> postfix operator
                result = mkPostfix(PostfixOp.IS_NULL, result);
                i++;
              } else if (nxt.equals("NOT")) {
                String nxt2 = ctx.getChild(i + 2).getText().toUpperCase(Locale.ROOT);
                if (nxt2.equals("NULL")) {
                  // IS NOT NULL -> postfix operator
                  result = mkPostfix(PostfixOp.IS_NOT_NULL, result);
                  i += 2;
                } else {
                  throw new IllegalStateException("Unsupported IS expression");
                }
              }

            } else if (up.equals("BETWEEN")) {
              Expr lower = (Expr) visit(ctx.shiftExpr().get(shiftIndex++));
              Expr upper = (Expr) visit(ctx.shiftExpr().get(shiftIndex++));
              Range r = new Range(result.range().start(), upper.range().end());
              result = new Ternary(r, TernaryOp.BETWEEN, result, lower, upper, new VoidT(r));
            } else if (up.equals("IN")) {
              ListV lits = listContext(ctx.literal(), this::visitLiteral, ListV::new);
              Range r = range(ctx);
              result = new InfixExpr(r, InfixOp.IN, result, lits, new VoidT(r));
            }
          }
          break;
      }
    }
    return result;
  }

  @Override
  public Expr visitAddExpr(SqlStreamParser.AddExprContext ctx) {
    ExprList exprs = listContext(ctx.mulExpr(), this::visitMulExpr, ExprList::new);
    if (exprs.size() == 1) return exprs.get(0);
    Expr res = exprs.get(0);
    for (int i = 1; i < exprs.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      InfixOp bop = op.equals("+") ? InfixOp.ADD : InfixOp.SUB;
      Expr right = exprs.get(i);
      res = mkInfix(bop, res, right);
    }
    return res;
  }

  @Override
  public Expr visitMulExpr(SqlStreamParser.MulExprContext ctx) {
    ExprList parts = listContext(ctx.unaryExpr(), this::visitUnaryExpr, ExprList::new);
    if (parts.size() == 1) return parts.get(0);
    Expr result = parts.get(0);
    for (int i = 1; i < parts.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      Expr right = parts.get(i);
      switch (op) {
        case "*":
          result = mkInfix(InfixOp.MUL, result, right);
          break;
        case "/":
          result = mkInfix(InfixOp.DIV, result, right);
          break;
        case "%":
          result = mkInfix(InfixOp.MOD, result, right);
          break;
        default:
          throw new IllegalStateException("Unknown multiplicative operator: " + op);
      }
    }
    return result;
  }

  @Override
  public Expr visitShiftExpr(SqlStreamParser.ShiftExprContext ctx) {
    ExprList parts = listContext(ctx.addExpr(), this::visitAddExpr, ExprList::new);
    if (parts.size() == 1) return parts.get(0);
    Expr res = parts.get(0);
    for (int i = 1; i < parts.size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      Expr right = parts.get(i);
      if (op.equals("<<")) {
        res = mkInfix(InfixOp.SHL, res, right);
      } else if (op.equals(">>")) {
        res = mkInfix(InfixOp.SHR, res, right);
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
      Range r = new Range(inner.range().start(), inner.range().end());
      return new PrefixExpr(r, PrefixOp.NEG, inner, new VoidT(r));
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

    // iterate parse-tree children so we preserve member/index order
    int childCount = ctx.getChildCount();
    for (int i = 1; i < childCount; i++) {
      var ch = ctx.getChild(i);
      if (ch instanceof SqlStreamParser.MemberAccessContext) {
        SqlStreamParser.MemberAccessContext ma = (SqlStreamParser.MemberAccessContext) ch;
        MemberExpr memberAccess = visitMemberAccess(ma);
        node = memberAccess.withTarget(node);
        continue;
      } else if (ch instanceof SqlStreamParser.IndexAccessContext) {
        SqlStreamParser.IndexAccessContext ix = (SqlStreamParser.IndexAccessContext) ch;
        IndexExpr indexAccess = visitIndexAccess(ix);
        node = indexAccess.withTarget(node);
        continue;
      } else {
        String t = ch.getText();
        if (t.equals(".") || t.equals("[") || t.equals("]"))
          continue;
        throw new IllegalStateException("Unexpected token in postfix expression: " + t);
      }
    }
    return node;
  }

  @Override
  public IndexExpr visitIndexAccess(SqlStreamParser.IndexAccessContext ctx) {
    Range range = range(ctx);
    Expr index = visitExpr(ctx.expr());
    return new IndexExpr(range, null, index, new VoidT(range));
  }

  @Override
  public MemberExpr visitMemberAccess(SqlStreamParser.MemberAccessContext ctx) {
    Range range = range(ctx);
    Identifier member = visitIdentifier(ctx.identifier());
    return new MemberExpr(range, null, member, new VoidT(range));
  }

  @Override
  public Expr visitPrimary(SqlStreamParser.PrimaryContext ctx) {
    Range range = range(ctx);
    if (ctx.LPAREN() != null) {
      return (Expr) visit(ctx.expr());
    }
    if (ctx.literal() != null) {
      return (Expr) visit(ctx.literal());
    }
    if (ctx.identifier() != null) {
      return new IdentifierExpr(range,visitIdentifier(ctx.identifier()), new VoidT(range));
    }
    throw new IllegalStateException("unknown primary");
  }

  @Override
  public AnyV visitLiteral(SqlStreamParser.LiteralContext c) {
    if (c.NULL() != null) return new NullV(range(c));
    if (c.literalValue() != null) return visitLiteralValue(c.literalValue());
    if (c.literalSeq() != null) return visitLiteralSeq(c.literalSeq());
    if (c.structLiteral() != null) return visitStructLiteral(c.structLiteral());
    if (c.enumLiteral() != null) return visitEnumLiteral(c.enumLiteral());
    if (c.unionLiteral() != null) return visitUnionLiteral(c.unionLiteral());
    throw new IllegalStateException("unknown literal");
  }

  @Override
  public PrimitiveV visitLiteralValue(SqlStreamParser.LiteralValueContext c) {
    Range range = range(c);
    if (c.TRUE() != null) return new BoolV(range, true);
    if (c.FALSE() != null) return new BoolV(range, false);
    if (c.STRING_LIT() != null) return new StringV(range(c), unquote(c.STRING_LIT().getText()));
    if (c.NUMBER_LIT() != null) return new Float64V(range(c), float64(c.NUMBER_LIT()));
    if (c.BYTES_LIT() != null) return new BytesV(range(c), bytes(c.BYTES_LIT()));
    throw new IllegalStateException("unknown literal value");
  }

  @Override
  public CompositeV visitLiteralSeq(SqlStreamParser.LiteralSeqContext c) {
    if (c.listLiteral() != null) {
      return visitListLiteral(c.listLiteral());
    } else if (c.mapLiteral() != null) {
      return visitMapLiteral(c.mapLiteral());
    }
    throw new IllegalStateException("unknown literal sequence");
  }

  @Override
  public ListV visitListLiteral(SqlStreamParser.ListLiteralContext c) {
    return listContext(c.literal(), this::visitLiteral, ListV::new);
  }

  @Override
  public MapV visitMapLiteral(SqlStreamParser.MapLiteralContext c) {
    return mapContext(c.mapEntry(), this::visitMapEntry, MapV::new);
  }

  @Override
  public AstMapEntryNode<PrimitiveV, AnyV> visitMapEntry(SqlStreamParser.MapEntryContext c) {
    Range range = range(c);
    PrimitiveV key = visitLiteralValue(c.literalValue());
    AnyV value = visitLiteral(c.literal());
    return new AstMapEntryNode<>(range, key, value);
  }

  @Override
  public StructV visitStructLiteral(SqlStreamParser.StructLiteralContext c) {
    return mapContext(c.structEntry(), this::visitStructEntry, StructV::new);
  }

  @Override
  public AstMapEntryNode<Identifier, AnyV> visitStructEntry(SqlStreamParser.StructEntryContext c) {
    Range range = range(c);
    Identifier fieldName = visitIdentifier(c.identifier());
    AnyV fieldValue = visitLiteral(c.literal());
    return new AstMapEntryNode<>(range, fieldName, fieldValue);
  }

  @Override
  public EnumV visitEnumLiteral(SqlStreamParser.EnumLiteralContext c) {
    Range range = range(c);
    Identifier enumName = visitIdentifier(c.identifier(0));
    Identifier symbol = visitIdentifier(c.identifier(1));
    return new EnumV(range, enumName, symbol);
  }

  @Override
  public UnionV visitUnionLiteral(SqlStreamParser.UnionLiteralContext c) {
    Range range = range(c);
    Identifier memberName = visitIdentifier(c.identifier());
    AnyV value = visitLiteral(c.literal());
    return new UnionV(range, memberName, value);
  }

  @Override
  public QName visitQname(SqlStreamParser.QnameContext q) {
    Range range = range(q);
    AstOptionalNode<DotPrefix> dotPrefix = AstOptionalNode.empty();
    IdentifierList parts = listContext(q.identifier(), this::visitIdentifier, IdentifierList::new);
    if (q.dotPrefix() != null) {
      var dp = visitDotPrefix(q.dotPrefix());
      dotPrefix = AstOptionalNode.of(dp);
    }
    return new QName(range, dotPrefix, parts);
  }

  @Override
  public DotPrefix visitDotPrefix(SqlStreamParser.DotPrefixContext ctx) {
    Range range = range(ctx);
    return new DotPrefix(range);
  }

  @Override
  public Identifier visitIdentifier(SqlStreamParser.IdentifierContext id) {
    Range range = range(id);
    return new Identifier(range, id.ID().getText());
  }

  private static Range range(ParserRuleContext ctx) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    return new Range(
      new Pos(start.getLine(), start.getCharPositionInLine()),
      new Pos(stop.getLine(), stop.getCharPositionInLine() + stop.getText().length())
    );
  }

  private static Pos start(ParserRuleContext ctx) {
    var start = ctx.getStart();
    return new Pos(start.getLine(), start.getCharPositionInLine());
  }

  private static Pos end(ParserRuleContext ctx) {
    var stop = ctx.getStop();
    return new Pos(stop.getLine(), stop.getCharPositionInLine() + stop.getText().length());
  }

  private static Expr fold(List<Expr> xs, InfixOp op) {
    if (xs == null || xs.isEmpty()) {
      throw new IllegalArgumentException("fold requires a non-empty list");
    }
    Expr acc = xs.get(0);
    for (int i = 1; i < xs.size(); i++) {
      Expr b = xs.get(i);
      Range r = new Range(acc.range().start(), b.range().end());
      acc = new InfixExpr(r, op, acc, b, new VoidT(r));
    }
    return acc;
  }

  public static double float64(TerminalNode n) {
    return Double.parseDouble(n.getText());
  }

  private static long int64(TerminalNode n) {
    return Long.parseLong(n.getText());
  }

  private static int int32(TerminalNode n) {
    return Integer.parseInt(n.getText());
  }

  private static byte int8(TerminalNode n) {
    return Byte.parseByte(n.getText());
  }

  private static byte[] bytes(TerminalNode n) {
    String str = n.getText();
    int index = 2; // skip "0x"
    int hexLen = str.length() - index;
    if ((hexLen & 1) != 0) throw new IllegalArgumentException("Hex literal must have even length");
    byte[] out = new byte[hexLen / 2];
    int outi = 0;
    while (index < str.length()) {
      int hi = Character.digit(str.charAt(index), 16);
      int lo = Character.digit(str.charAt(index + 1), 16);
      if (hi == -1 || lo == -1) {
        throw new IllegalArgumentException("Invalid hex character in bytes literal: " +
            str.substring(index, index + 2));
      }
      out[outi++] = (byte) ((hi << 4) | lo);
      index += 2;
    }
    return out;
  }

  // helper to create infix with a proper range/type
  private static InfixExpr mkInfix(InfixOp op, Expr left, Expr right) {
    Range r = new Range(left.range().start(), right.range().end());
    return new InfixExpr(r, op, left, right, new VoidT(r));
  }

  // helper to create postfix with a proper range/type
  private static PostfixExpr mkPostfix(PostfixOp op, Expr operand) {
    Range r = new Range(operand.range().start(), operand.range().end());
    return new PostfixExpr(r, op, operand, new VoidT(r));
  }

  private static String unquote(String s) {
    if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'')
      return s.substring(1, s.length() - 1).replace("''", "'");
    return s;
  }

  public static <C extends ParserRuleContext, T extends AstNode, L extends AstListNode<T>> L listContext(
      List<C> input,
      Function<? super C, ? extends T> transform,
      Function<? super Range, ? extends L> listFactory
  ) {
    Pos upperLeft = start(input.get(0));
    Pos bottomRight = end(input.get(input.size() - 1));
    Range rn = new Range(upperLeft, bottomRight);
    L result = listFactory.apply(rn);
    input.forEach(c -> result.add(transform.apply(c)));
    return result;
  }

  public static <C extends ParserRuleContext, KT extends AstNode, VT extends AstNode, M extends AstMapNode<KT, VT>> M mapContext(
      List<C> input,
      Function<? super C, ? extends AstMapEntryNode<KT, VT>> transform,
      Function<? super Range, ? extends M> mapFactory
  ) {
    Pos upperLeft = start(input.get(0));
    Pos bottomRight = end(input.get(input.size() - 1));
    Range rn = new Range(upperLeft, bottomRight);
    M result = mapFactory.apply(rn);
    for (C c : input) {
      AstMapEntryNode<KT, VT> e = transform.apply(c);
      result.put(e.key(), e.value());
    }
    return result;
  }
}
