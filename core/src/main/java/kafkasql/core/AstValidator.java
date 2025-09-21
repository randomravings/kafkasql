package kafkasql.core;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import kafkasql.core.ast.*;

public final class AstValidator {
  public final Catalog catalog = new Catalog();
  private final Diagnostics diags;

  private QName currentContext = QName.ROOT;

  public AstValidator(Diagnostics diags) {
    this.diags = diags;
  }

  public Diagnostics diags() {
    return diags;
  }

  public Ast validate(Ast ast) {
    if (diags.hasErrors())
      throw new IllegalStateException("Validator used after errors detected");

    Ast result = new Ast();
    for (Stmt stmt : ast) {
      Stmt s = validateStmt(stmt);
      result.add(s);
      if (diags.hasErrors())
        break;
    }
    return result;
  }

  private boolean contextNotSet(Range range) {
    if (currentContext.isRoot()) {
      diags.addFatal(range, "Only CONTEXT statements allowed at the root. Use `USE CONTEXT <name>;` first.");
      return true;
    }
    return false;
  }

  private Stmt validateStmt(Stmt s) {
    return switch (s) {
      case UseStmt us -> validateUseStmt(us);
      case CreateStmt cs -> validateCreateStmt(cs);
      case ReadStmt rq -> validateReadStmt(rq);
      case WriteStmt ws -> validateWriteStmt(ws);
      default -> s;
    };
  }

  private UseStmt validateUseStmt(UseStmt us) {
    return switch (us) {
      case UseContext uc -> validateUseContext(uc);
      default -> us;
    };
  }

  private UseContext validateUseContext(UseContext uc) {
    var fqn = uc.qname();
    if (fqn.isRoot()) {
      currentContext = fqn;
      return uc;
    }

    Optional<Context> ctx = catalog.getContext(currentContext, fqn);
    if (!ctx.isPresent()) {
      diags.addError(uc.range(),
          "Unknown context '" + fqn.fullName() + "' as child of '" + currentContext.fullName() + "'");
    } else {
      if (ctx.get().qName().dotPrefix().isPresent()) {
        currentContext = fqn;
      } else {
        var parts = new IdentifierList(fqn.range());
        parts.addAll(currentContext.parts());
        parts.addAll(fqn.parts());
        currentContext = new QName(fqn.range(), fqn.dotPrefix(), parts);
      }
    }
    return uc;
  }

  private CreateStmt validateCreateStmt(CreateStmt cs) {
    return switch (cs) {
      case CreateContext cd -> validateCreateContext(cd);
      case CreateType td -> validateCreateType(td);
      case CreateStream as -> validateStream(as);
      default -> cs;
    };
  }

  private CreateContext validateCreateContext(CreateContext cd) {
    var fqn = cd.context().qName();
    if (catalog.containsKey(currentContext, fqn)) {
      diags.addError(cd.range(), "Context '" + fqn + "' already defined at '" + currentContext.fullName() + "'");
    } else {
      catalog.put(currentContext, cd.context());
    }
    return cd;
  }

  private CreateType validateCreateType(CreateType ct) {
    if (contextNotSet(ct.range()))
      return ct;
    if (catalog.containsKey(currentContext, ct.qName())) {
      diags.addError(ct.range(), "Type '" + ct.qName() + "' already defined at '" + currentContext.fullName() + "'");
      return ct;
    }
    ComplexT type = switch (ct.type()) {
      case EnumT tp -> validateEnum(tp);
      case StructT tp -> validateStruct(tp);
      case UnionT tp -> validateUnion(tp);
      case ScalarT tp -> validateScalar(tp);
    };
    if (diags.hasErrors())
      return ct;
    catalog.put(currentContext, type);
    return new CreateType(ct.range(), type);
  }

  private EnumT validateEnum(EnumT tp) {
    AstOptionalNode<IntegerT> enumBase = tp.type();
    EnumSymbolList converted = alignEnumSymbols(tp.symbols(), enumBase);
    if (diags.hasErrors())
      return tp;

    if (tp.defaultValue().isPresent()) {
      var enumName = tp.qName().name();
      var defaultEnumName = tp.defaultValue().get().enumName().name();
      if (!defaultEnumName.equals(enumName)) {
        diags.addError(tp.defaultValue().get().range(),
            "Enum '" + tp.qName().fullName() + "' default symbol refers to wrong enum '"
                + defaultEnumName + "'");
      } else {
        var defaultSymbol = tp.defaultValue().get().symbol().name();
        boolean found = converted.stream().anyMatch(s -> s.name().name().equals(defaultSymbol));
        if (!found) {
          diags.addError(tp.range(),
              "Enum '" + tp.qName().fullName() + "' default symbol '" + defaultSymbol
                  + "' is not one of the enum symbols");
        }
      }
    }
    if (diags.hasErrors())
      return tp;
    return new EnumT(tp.range(), tp.qName(), enumBase, converted, tp.defaultValue());
  }

  private EnumSymbolList alignEnumSymbols(EnumSymbolList e, AstOptionalNode<IntegerT> type) {
    IntegerT base = new Int32T(Range.NONE);
    if (type.isPresent())
      base = type.get();
    EnumSymbolList converted = new EnumSymbolList(e.range());
    for (EnumSymbol sym : e) {
      if (diags.hasErrors())
        return converted;

      IntegerV v = alignIntegerValue(sym.value(), base);

      if (diags.hasErrors()) {
        diags.addError(sym.range(),
            "Enum symbol value for '" + sym.name().name() + "' has unsupported underlying integer type");
        continue;
      }

      converted.add(new EnumSymbol(e.range(), sym.name(), v));
    }
    return converted;
  }

  private StructT validateStruct(StructT tp) {
    QName fqn = tp.qName();
    if (tp.fieldList().isEmpty()) {
      diags.addError(tp.range(), "Struct '" + fqn.fullName() + "' must have at least one field");
      return tp;
    }
    var seen = new HashSet<String>();
    for (var field : tp.fieldList()) {
      if (!seen.add(field.name().name()))
        diags.addError(field.range(),
            "Struct '" + tp.qName().fullName() + "' reuses field name '" + field.name().name() + "'");
    }
    if (diags.hasErrors())
      return tp;
    return tp;
  }

  private UnionT validateUnion(UnionT tp) {
    var fqn = tp.qName();
    if (tp.types().isEmpty()) {
      diags.addError(tp.range(), "Union '" + fqn.fullName() + "' must have at least one option");
      return tp;
    }
    var seen = new HashSet<String>();
    for (var opt : tp.types()) {
      if (!seen.add(opt.name().name()))
        diags.addError(opt.range(), "Union '" + fqn.fullName() + "' reuses option name '" + opt.name().name() + "'");
    }
    if (diags.hasErrors())
      return tp;
    return tp;
  }

  private ScalarT validateScalar(ScalarT tp) {
    QName fqn = tp.qName();
    PrimitiveT pt = tp.primitive();
    AstOptionalNode<Expr> vl = tp.validation();
    AstOptionalNode<PrimitiveV> dv = tp.defaultValue();
    if (catalog.containsKey(currentContext, fqn)) {
      diags.addError(tp.range(), "Type '" + fqn + "' already defined");
      return tp;
    }
    if (dv.isPresent()) {
      dv = AstOptionalNode.of(alignPrimitive(dv.get(), pt));
      if (diags.hasErrors())
        return tp;
    }

    if (vl.isPresent()) {
      IntegerT ct = new Int32T(Range.NONE);
      Map<String, AnyT> symbols = new HashMap<>();
      symbols.put("value", pt);
      Expr sexpr = trimExpression(vl.get(), ct, symbols);
      if (diags.hasErrors())
        return tp;
      vl = AstOptionalNode.of(sexpr);
    }

    return new ScalarT(tp.range(), fqn, pt, vl, dv);
  }

  private CreateStream validateStream(CreateStream cs) {
    if (contextNotSet(cs.range()))
      return null;
    var fqn = cs.qName();
    if (catalog.containsKey(currentContext, fqn)) {
      diags.addError(cs.range(),
          "Stream '" + fqn.fullName() + "' already defined for context '" + currentContext.fullName() + "'");
      return cs;
    }
    var stream = cs.stream();
    var seen = new HashSet<String>();
    for (var alt : stream.types()) {
      if (diags.hasErrors())
        return cs;
      Identifier alias = alt.alias();
      Set<String> keySet = new HashSet<>();
      AstOptionalNode<DistributeClause> distributeClause = alt.distributeClause();
      AstListNode<Field> fields = null;
      if (!seen.add(alias.name()))
        diags.addError(alt.range(), "Stream '" + fqn.fullName() + "' reuses alias '" + alias.name() + "'");
      if (alt instanceof StreamReferenceT ra) {
        var tname = ra.ref().qName();
        Optional<StructT> structOpt = catalog.getStruct(currentContext, tname);
        if (structOpt.isEmpty()) {
          diags.addError(ra.range(),
              "Stream '" + fqn.fullName() + "' references unknown type '" + tname.fullName() + "'");
          continue;
        }
        fields = structOpt.get().fieldList();
      } else if (alt instanceof StreamInlineT it) {
        fields = it.fields();
      } else {
        diags.addFatal(alt.range(), "Unexpected stream type definition");
        continue;
      }

      if (!distributeClause.isPresent())
        continue; // optional

      // Uniqueness
      for (Identifier k : distributeClause.get().keys()) {
        if (!keySet.add(k.name())) {
          diags.addError(k.range(),
              "Duplicate field '" + k + "' in DISTRIBUTE clause for stream " +
                  stream.qName().fullName() + " type alias '" +
                  alias.name() + "'.");
        }
      }

      // Field existence
      keySet = fields.stream().map(f -> f.name().name()).collect(Collectors.toSet());

      for (Identifier k : distributeClause.get().keys()) {
        if (!keySet.contains(k.name())) {
          diags.addError(k.range(),
              "Field '" + k + "' not found in type for DISTRIBUTE clause (stream " +
                  stream.qName().fullName() + ").");
        }
      }
    }
    var newStream = new StreamT(cs.range(), fqn, stream.types());
    catalog.put(currentContext, newStream);
    return new CreateStream(cs.range(), newStream);
  }

  private ReadStmt validateReadStmt(ReadStmt rs) {
    Optional<StreamT> streamOpt = catalog.getStream(currentContext, rs.stream());
    if (streamOpt.isEmpty()) {
      diags.addError(rs.range(), "Unknown stream '" + rs.stream().fullName() + "'");
      return rs;
    }
    StreamT stream = streamOpt.get();

    for (var block : rs.blocks()) {
      Set<Identifier> fields = topLevelFields(stream, block.alias(), catalog);
      if (fields == null) {
        diags.addError(block.alias().range(),
            "Stream '" + rs.stream().fullName() + "': unknown TYPE alias '" + block.alias().name() + "'");
        continue;
      }
      if (block.projection() instanceof ProjectionAll)
        continue;
      if (block.projection() instanceof ProjectionList col) {
        for (ProjectionExpr p : col) {
          validatePath(p, fields, rs, block);
        }
      } else {
        diags.addFatal(block.projection().range(),
            "Unexpected projection type: " + block.projection().getClass().getSimpleName());
      }
    }

    return rs;
  }

  private ProjectionExpr validatePath(ProjectionExpr p, Set<Identifier> fields, ReadStmt r, ReadTypeBlock block) {
    return p;
  }

  private WriteStmt validateWriteStmt(WriteStmt ws) {
    if (contextNotSet(ws.range()))
      return null;
    var fqn = ws.stream();
    Optional<StreamT> streamOpt = catalog.getStream(currentContext, ws.stream());
    if (streamOpt.isEmpty()) {
      diags.addError(ws.range(), "Unknown stream '" + fqn.fullName() + "'");
      return ws;
    }
    StreamT ds = streamOpt.get();

    Set<Identifier> fields = topLevelFields(ds, ws.alias(), catalog);
    if (fields == null) {
      diags.addError(ws.alias().range(),
          "Stream '" + fqn.fullName() + "': unknown TYPE alias '" + ws.alias().name() + "'");
      return ws;
    }

    return ws;
  }

  private Set<Identifier> topLevelFields(StreamT s, Identifier alias, Catalog cat) {
    StreamType alt = s.types().stream().filter(t -> t.alias().name().equals(alias.name())).findFirst().orElse(null);
    if (alt instanceof StreamInlineT ia)
      return ia.fields().stream().map(Field::name).collect(Collectors.toSet());
    if (alt instanceof StreamReferenceT ra) {
      Optional<StructT> td = cat.getStruct(currentContext, ra.ref().qName());
      if (td.isEmpty())
        return null;
      return td.get().fieldList().stream().map(Field::name).collect(Collectors.toSet());
    }
    return null;
  }

  private Expr trimExpression(Expr e, AnyT constrainingType, Map<String, AnyT> symbols) {
    switch (e) {
      case PostfixExpr pe:
        return trimPostfix(pe, constrainingType, symbols);
      case PrefixExpr ue:
        return trimUnary(ue, constrainingType, symbols);
      case InfixExpr be:
        return trimBinary(be, constrainingType, symbols);
      case Ternary te:
        return trimTernary(te, constrainingType, symbols);
      case IdentifierExpr s:
        // Handle symbol trimming
        break;
      case MemberExpr me:
        // Handle member expression trimming
        break;
      case IndexExpr ie:
        // Handle index expression trimming
        break;
      case AnyV av:
        // Handle any value trimming
        break;
    }
    return e;
  }

  private PostfixExpr trimPostfix(PostfixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
    return e;
  }

  private PrefixExpr trimUnary(PrefixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
    return e;
  }

  private InfixExpr trimBinary(InfixExpr e, AnyT constrainingType, Map<String, AnyT> symbols) {
    Expr l = trimExpression(e.left(), constrainingType, symbols);
    Expr r = trimExpression(e.right(), constrainingType, symbols);
    return e;
  }

  private Ternary trimTernary(Ternary e, AnyT constrainingType, Map<String, AnyT> symbols) {
    Expr l = trimExpression(e.left(), constrainingType, symbols);
    Expr m = trimExpression(e.middle(), constrainingType, symbols);
    Expr r = trimExpression(e.right(), constrainingType, symbols);
    return e;
  }

  private AnyV alignValue(AnyV value, AnyT type) {
    if (type instanceof VoidT) {
      diags.addError(value.range(), "Cannot assign value to VOID type");
      return value;
    }

    AnyV result = value;

    switch (type) {
      case PrimitiveT pt:
        if (value instanceof PrimitiveV l)
          result = alignPrimitive(l, pt);
        else
          diags.addError(value.range(),
              "Expected literal value for primitive type, got '" + value.getClass().getSimpleName() + "'");
        break;
      case ComplexT ct:
        diags.addError(value.range(), "Cannot assign value to COMPLEX type (yet ...)");
        break;
      case CompositeT ct:
        diags.addError(value.range(), "Cannot assign value to COMPOSITE type (yet ...)");
        break;
      case TypeReference tr:
        diags.addError(value.range(), "Cannot assign value to TYPE REF (yet ...)");
        break;
      default:
        diags.addError(value.range(), "Unsupported combination '" + type.getClass().getSimpleName() + "' and  '"
            + value.getClass().getSimpleName() + "'");
        break;
    }
    return result;
  }

  private PrimitiveV alignPrimitive(PrimitiveV lit, PrimitiveT type) {
    PrimitiveV result = lit;
    switch (type) {
      case BoolT __:
        if (!(lit instanceof BoolV))
          diags.addError(lit.range(), "Expected BOOL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        break;
      case AlphaT __:
        if (!(lit instanceof AlphaV))
          diags.addError(lit.range(), "Expected ALPHA literal, got '" + lit.toString() + "'");
        break;
      case BinaryT __:
        if (!(lit instanceof BinaryV))
          diags.addError(lit.range(),
              "Expected BINARY literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        break;
      case NumberT nt:
        if (!(lit instanceof NumberV nv))
          diags.addError(lit.range(),
              "Expected NUMBER literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        else
          result = alignNumericValue(nv, (NumberT) type);
        break;
      case TemporalT tt:
        if (!(lit instanceof TemporalV))
          diags.addError(lit.range(),
              "Expected TEMPORAL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        break;
      default:
        diags.addError(lit.range(),
            "Unsupported combination of literal type '" + type.getClass().getSimpleName() + "' and literal '"
                + lit.getClass().getSimpleName() + "'");
        break;
    }
    return result;
  }

  private NumberV alignNumericValue(NumberV value, NumberT type) {

    if (value instanceof IntegerV iv && type instanceof IntegerT it) {
      // both integer
      return alignIntegerValue(iv, it);
    } else if (value instanceof FractionalV fv && type instanceof FractionalT ft) {
      // both fractional
      return alignFractionalValue(fv, ft);
    } else if (value instanceof IntegerV iv && type instanceof FractionalT ft) {
      // integer to fractional
      double v = getIntegerValue(iv);
      return alignFractionalValue(new Float64V(iv.range(), v), ft);
    } else {
      // Safeguard for future implementations
      diags.addError(value.range(), "Unsupported numeric value type '" + value.getClass().getSimpleName() + "'");
      return value;
    }
  }

  private FractionalV alignFractionalValue(FractionalV value, FractionalT type) {
    double v = getFractionalValue(value);
    FractionalV result = value;
    switch (type) {
      case DecimalT d:
        var dec = BigDecimal.valueOf(v);
        if (dec.precision() > d.precision() || dec.scale() > d.scale())
          diags.addError(value.range(),
              "Value '" + v + "' does not fit in DECIMAL(" + d.precision() + "," + d.scale() + ")");
        else
          result = new DecimalV(value.range(), dec);
        break;
      case Float32T __:
        if (v < Float.MIN_VALUE || v > Float.MAX_VALUE)
          diags.addError(value.range(), "Value '" + v + "' does not fit in FLOAT32");
        else
          result = new Float32V(value.range(), (float) v);
        break;
      case Float64T __:
        // all doubles fit in Float64
        break;
      default:
        // Safeguard for future implementations
        diags.addError(value.range(), "Unknown fractional type '" + type.toString() + "'");
    }
    return result;
  }

  private IntegerV alignIntegerValue(IntegerV value, IntegerT type) {
    long v = getIntegerValue(value);
    IntegerV result = value;
    switch (type) {
      case Int8T __:
        if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE)
          diags.addError(value.range(), "Value '" + v + "' does not fit in INT8");
        else
          result = new Int8V(value.range(), (byte) v);
        break;
      case Int16T __:
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE)
          diags.addError(value.range(), "Value '" + v + "' does not fit in INT16");
        else
          result = new Int16V(value.range(), (short) v);
        break;
      case Int32T __:
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
          diags.addError(value.range(), "Value '" + v + "' does not fit in INT32");
        else
          result = new Int32V(value.range(), (int) v);
        break;
      case Int64T __:
        // all longs fit in Int64
        break;
      default:
        // Safeguard for future implementations
        diags.addError(value.range(), "Unknown integer type '" + type.toString() + "'");
        break;
    }
    return result;
  }

  private static long getIntegerValue(IntegerV v) {
    return switch (v) {
      case Int8V iv -> iv.value();
      case Int16V iv -> iv.value();
      case Int32V iv -> iv.value();
      case Int64V iv -> iv.value();
    };
  }

  private static double getFractionalValue(FractionalV v) {
    return switch (v) {
      case Float32V fv -> fv.value();
      case Float64V fv -> fv.value();
      case DecimalV dv -> dv.value().doubleValue();
    };
  }

  public static double getNumericValue(NumberV v) {
    return switch (v) {
      case IntegerV iv -> getIntegerValue(iv);
      case FractionalV fv -> getFractionalValue(fv);
    };
  }
}
