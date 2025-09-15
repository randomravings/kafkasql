package streamsql;

import streamsql.ast.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public final class Validator {
  public final Catalog catalog;
  private final Diagnostics diags = new Diagnostics();

  private QName currentContext = QName.root();
  private List<Stmt> stmts = new ArrayList<>();

  public Validator(Catalog catalog) {
    this.catalog = catalog;
  }

  public Validator(Catalog catalog, QName initialContext) {
    this.catalog = catalog;
    this.currentContext = initialContext;
  }

  public ParseResult validate(List<Stmt> tree) {
    if (diags.hasErrors())
      throw new IllegalStateException("Validator used after errors detected");

    for (Stmt stmt : tree) {
      Stmt s = switch (stmt) {
        case UseStmt us -> validateUseStmt(us);
        case CreateStmt cs -> validateCreateStmt(cs);
        case ReadStmt rq -> validateReadStmt(rq);
        case WriteStmt ws -> validateWriteStmt(ws);
        default -> stmt;
      };
      if (diags.hasErrors())
        break;
      else
        stmts.add(s);
    }
    return new ParseResult(stmts, diags);
  }

  private boolean contextNotSet() {
    if (currentContext.isRoot()) {
      diags.error("Only CONTEXT statements allowed at the root. Use `USE CONTEXT <name>;` first.");
      return true;
    }
    return false;
  }

  // return new context when use succeeds
  private UseStmt validateUseStmt(UseStmt us) {
    return switch (us) {
      case UseContext uc -> validateUseContext(uc);
      default -> us;
    };
  }

  private UseContext validateUseContext(UseContext uc) {
    var fqn = uc.absolute() ? uc.context().qName() : QName.join(currentContext, uc.context().qName());
    Optional<Context> ctx = catalog.getContext(fqn);
    if (!ctx.isPresent()) {
      diags.error("Unknown context '" + fqn.fullName() + "'");
    } else {
      currentContext = fqn;
    }
    return new UseContext(new Context(fqn), true);
  }

  private CreateStmt validateCreateStmt(CreateStmt cs) {
    return switch (cs) {
      case CreateContext cd -> validateCreateContext(cd);
      case CreateType td -> validateCreateType(td);
      case CreateStream as -> validateAppendStreamDecl(as);
      default -> cs;
    };
  }

  private CreateContext validateCreateContext(CreateContext cd) {
    var fqn = QName.join(currentContext, cd.qName());
    var ctx = new Context(fqn);
    if (catalog.containsKey(fqn)) {
      diags.error("Context '" + fqn + "' already defined");
    } else {
      catalog.put(ctx);
    }
    return new CreateContext(ctx);
  }

  // Now returns Optional<CreateType> so a normalized CreateEnum can be swapped
  // into the statement list
  private CreateType validateCreateType(CreateType ct) {
    if (contextNotSet())
      return ct;
    var fqn = QName.join(currentContext, ct.qName());
    if (catalog.containsKey(fqn)) {
      diags.error("Type '" + fqn + "' already defined");
      return ct;
    }
    switch (ct) {
      case CreateEnum ce:
        return validateCreateEnum(ce);
      case CreateStruct cs:
        return validateCreateStruct(cs);
      case CreateUnion cu:
        return validateCreateUnion(cu);
      case CreateScalar cs:
        return validateCreateScalar(cs);
      default:
        return ct;
    }
  }

  private CreateEnum validateCreateEnum(CreateEnum ce) {
    streamsql.ast.EnumT e = ce.type();
    IntegerT enumBase = e.type();
    var fqn = QName.join(currentContext, ce.qName());

    List<EnumSymbol> converted = alignEnumSymbols(e.symbols(), enumBase);
    if (diags.hasErrors())
      return ce;

    // validate default symbol (if present) refers to one of the defined symbols
    if (e.defaultSymbol().isPresent()) {
      Identifier def = e.defaultSymbol().get();
      boolean found = converted.stream().anyMatch(s -> s.name().equals(def));
      if (!found) {
        diags.error(
            "Enum '" + e.qName().fullName() + "' default symbol '" + def.value() + "' is not one of the enum symbols");
      }
      if (diags.hasErrors())
        return ce;
    }
    var ne = new CreateEnum(new streamsql.ast.EnumT(fqn, enumBase, e.isMask(), converted, e.defaultSymbol()));
    catalog.put(ne.type());
    return ne;
  }

  private List<EnumSymbol> alignEnumSymbols(List<EnumSymbol> e, IntegerT type) {
    List<EnumSymbol> converted = new ArrayList<>();
    for (EnumSymbol sym : e) {
      if (diags.hasErrors())
        return converted;

      IntegerV v = alignIntegerValue(sym.value(), type);

      if (diags.hasErrors()) {
        diags.error("Enum symbol value for '" + sym.name().value() + "' has unsupported underlying integer type");
        continue;
      }

      converted.add(new EnumSymbol(sym.name(), v));
    }
    return converted;
  }

  private CreateStruct validateCreateStruct(CreateStruct cs) {
    if (cs.type().fields().isEmpty()) {
      diags.error("Struct '" + cs.qName().fullName() + "' must have at least one field");
      return cs;
    }
    var fqn = QName.join(currentContext, cs.qName());
    if (catalog.containsKey(fqn)) {
      diags.error("Type '" + fqn + "' already defined");
      return cs;
    }
    var seen = new HashSet<String>();
    for (var field : cs.type().fields()) {
      if (!seen.add(field.name().value()))
        diags.error("Struct '" + fqn + "' reuses field name '" + field.name().value() + "'");
    }
    if (diags.hasErrors())
      return cs;
    var struct = new CreateStruct(new StructT(fqn, cs.type().fields()));
    catalog.put(struct.type());
    return struct;
  }

  private CreateUnion validateCreateUnion(CreateUnion cu) {
    if (cu.type().types().isEmpty()) {
      diags.error("Union '" + cu.qName().fullName() + "' must have at least one option");
      return cu;
    }
    var fqn = QName.join(currentContext, cu.qName());
    if (catalog.containsKey(fqn)) {
      diags.error("Type '" + fqn + "' already defined");
      return cu;
    }
    var seen = new HashSet<String>();
    for (var opt : cu.type().types()) {
      if (!seen.add(opt.name().value()))
        diags.error("Union '" + fqn + "' reuses option name '" + opt.name().value() + "'");
    }
    if (diags.hasErrors())
      return cu;
    var union = new CreateUnion(new UnionT(fqn, cu.type().types()));
    catalog.put(union.type());
    return union;
  }

  private CreateScalar validateCreateScalar(CreateScalar cs) {
    QName fqn = QName.join(currentContext, cs.qName());
    PrimitiveT pt = cs.type().primitive();
    Optional<Expr> vl = cs.type().validation();
    Optional<PrimitiveV> dv = cs.type().defaultValue();
    if (catalog.containsKey(fqn)) {
      diags.error("Type '" + fqn + "' already defined");
      return cs;
    }
    if (dv.isPresent()) {
      dv = Optional.of(alignPrimitive(dv.get(), pt));
      if (diags.hasErrors())
        return cs;
    }

    // Validate CHECK expression (if provided)
    if (vl.isPresent()) {
      Map<String, AnyT> symbols = new HashMap<>();
      symbols.put("value", pt);
      Expr sexpr = trimExpression(vl.get(), BoolT.get(), symbols);
      if (diags.hasErrors())
        return cs;
      vl = Optional.of(sexpr);
    }

    var scalar = new ScalarT(fqn, pt, vl, dv);
    catalog.put(cs.type());
    return new CreateScalar(scalar);
  }

  private CreateStream validateAppendStreamDecl(CreateStream cs) {
    if (contextNotSet())
      return null;
    var fqn = QName.join(currentContext, cs.qName());
    if (catalog.containsKey(fqn)) {
      diags.error("Stream '" + fqn + "' already defined");
      return cs;
    }
    var stream = cs.stream();
    var seen = new HashSet<String>();
    for (var alt : stream.types()) {
      Identifier alias = alt.alias();
      if (!seen.add(alias.value()))
        diags.error("Stream '" + fqn + "' reuses alias '" + alias.value() + "'");
      if (alt instanceof StreamReferenceT ra) {
        var tname = ra.ref().qName();
        if (!catalog.containsKey(tname))
          diags.error("Stream '" + fqn + "' references unknown type '" + tname + "'");
      }
    }
    var newStream = new StreamLog(fqn, stream.types());
    catalog.put(newStream);
    return new CreateStream(newStream);
  }

  private ReadStmt validateReadStmt(ReadStmt rs) {
    if (contextNotSet())
      return null;
    var fqn = rs.stream();
    if (fqn.parts().size() == 1) {
      fqn = currentContext.append(fqn.parts().get(0));
    }
    Optional<DataStream> streamOpt = catalog.getStream(fqn);
    if (streamOpt.isEmpty()) {
      diags.error("Unknown stream '" + fqn.fullName() + "'");
      return rs;
    }
    DataStream stream = streamOpt.get();

    for (var block : rs.blocks()) {
      Set<Identifier> fields = topLevelFields(stream, block.alias(), catalog);
      if (fields == null) {
        diags.error("Stream '" + rs.stream().fullName() + "': unknown TYPE alias '" + block.alias().value() + "'");
        continue;
      }
      if (block.projection() instanceof ProjectionAll)
        continue;
      if (block.projection() instanceof ProjectionList col) {
        for (ProjectionExpr p : col.items()) {
          validatePath(p, fields, rs, block);
        }
      } else {
        diags.fatal("Unexpected projection type: " + block.projection().getClass().getSimpleName());
      }
    }

    return rs;
  }

  private ProjectionExpr validatePath(ProjectionExpr p, Set<Identifier> fields, ReadStmt r, ReadSelection block) {
    return p;
  }

  private WriteStmt validateWriteStmt(WriteStmt ws) {
    if (contextNotSet())
      return null;
    var fqn = ws.stream();
    if (fqn.parts().size() == 1) {
      fqn = currentContext.append(fqn.parts().get(0));
    }
    Optional<DataStream> streamOpt = catalog.getStream(fqn);
    if (streamOpt.isEmpty()) {
      diags.error("Unknown stream '" + fqn.fullName() + "'");
      return ws;
    }
    DataStream ds = streamOpt.get();

    Set<Identifier> fields = topLevelFields(ds, ws.alias(), catalog);
    if (fields == null) {
      diags.error("Stream '" + fqn.fullName() + "': unknown TYPE alias '" + ws.alias().value() + "'");
      return ws;
    }

    return ws;
  }

  private Set<Identifier> topLevelFields(DataStream s, Identifier alias, Catalog cat) {
    StreamType alt = s.types().stream().filter(t -> t.alias().equals(alias)).findFirst().orElse(null);
    if (alt instanceof StreamInlineT ia)
      return ia.fields().stream().map(Field::name).collect(Collectors.toSet());
    if (alt instanceof StreamReferenceT ra) {
      Optional<StructT> td = cat.getStruct(ra.ref().qName());
      if (td.isEmpty())
        return null;
      return td.get().fields().stream().map(Field::name).collect(Collectors.toSet());
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
      case Symbol s:
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
      diags.error("Cannot assign value to VOID type");
      return value;
    }
  
    AnyV result = value;

    switch (type) {
      case PrimitiveT pt:
        if (value instanceof PrimitiveV l)
          result = alignPrimitive(l, pt);
        else
          diags.error("Expected literal value for primitive type, got '" + value.getClass().getSimpleName() + "'");
        break;
      case ComplexT ct:
        diags.error("Cannot assign value to COMPLEX type (yet ...)");
        break;
      case CompositeT ct:
        diags.error("Cannot assign value to COMPOSITE type (yet ...)");
        break;
      case TypeRef tr:
        diags.error("Cannot assign value to TYPE REF (yet ...)");
        break;
      default:
        diags.error("Unsupported combination '" + type.getClass().getSimpleName() + "' and  '" + value.getClass().getSimpleName() + "'");
        break;
    }
    return result;
  }

  private PrimitiveV alignPrimitive(PrimitiveV lit, PrimitiveT type) {
    PrimitiveV result = lit;
    switch (type) {
      case BoolT __:
        if (!(lit instanceof BoolV))
          diags.error("Expected BOOL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        break;
      case AlphaT __:
        if(!(lit instanceof AlphaV))
          diags.error("Expected ALPHA literal, got '" + lit.toString() + "'");
        break;
      case BinaryT __:
        if (!(lit instanceof BinaryV))
          diags.error("Expected BINARY literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        break;
      case NumberT nt:
        if (!(lit instanceof NumberV nv))
          diags.error("Expected NUMBER literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        else
          result = alignNumericValue(nv, (NumberT) type);
        break;
      case TemporalT tt:
        if (!(lit instanceof TemporalV))
          diags.error("Expected TEMPORAL literal, got '" + lit.getClass().getSimpleName().toString() + "'");
        break;
      default:
        diags.error("Unsupported combination of literal type '" + type.getClass().getSimpleName() + "' and literal '" + lit.getClass().getSimpleName() + "'");
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
      var v = (double) iv.value();
      return alignFractionalValue(new Float64V(v), ft);
    } else {
      // Safeguard for future implementations
      diags.error("Unsupported numeric value type '" + value.getClass().getSimpleName() + "'");
      return Float64V.ZERO;
    }
  }

  private FractionalV alignFractionalValue(FractionalV value, FractionalT type) {
    double v = value.value();
    FractionalV result = value;
    switch (type) {
      case DecimalT d:
        var dec = BigDecimal.valueOf(v);
        if (dec.precision() > d.precision().value() || dec.scale() > d.scale().value())
          diags.error("Value '" + value.value() + "' does not fit in DECIMAL(" + d.precision() + "," + d.scale() + ")");
        else
          result = new DecimalV(dec);
        break;
      case Float32T __:
        if (v < Float.MIN_VALUE || v > Float.MAX_VALUE)
          diags.error("Value '" + value.value() + "' does not fit in FLOAT32");
        else
          result = new Float32V((float) v);
        break;
      case Float64T __:
        // all doubles fit in Float64
        break;
      default:
        // Safeguard for future implementations
        diags.error("Unknown fractional type '" + type.toString() + "'");
        result = Float64V.ZERO;
    }
    return result;
  }

  private IntegerV alignIntegerValue(IntegerV value, IntegerT type) {
    long v = value.value();
    IntegerV result = value;
    switch (type) {
      case Int8T __:
        if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE)
          diags.error("Value '" + value.value() + "' does not fit in INT8");
        else
          result = new Int8V((byte) v);
          break;
      case Int16T __:
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE)
          diags.error("Value '" + value.value() + "' does not fit in INT16");
        else
          result = new Int16V((short) v);
        break;
      case Int32T __:
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
          diags.error("Value '" + value.value() + "' does not fit in INT32");
        else
          result = new Int32V((int) v);
        break;
      case Int64T __:
        // all longs fit in Int64
        break;
      default:
        // Safeguard for future implementations
        diags.error("Unknown integer type '" + type.toString() + "'");
        break;
    }
    return result;
  }
}
