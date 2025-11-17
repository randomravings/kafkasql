package kafkasql.lang;

import java.util.*;
import java.util.stream.Collectors;

import kafkasql.lang.ast.*;
import kafkasql.lang.validation.ExprValidator;

public final class AstValidator {
  public final NodeCatalog catalog = new NodeCatalog();
  private final Diagnostics diags;
  private final ExprValidator exprValidator;

  private QName currentContext = QName.ROOT;

  public AstValidator(Diagnostics diags) {
    this.diags = diags;
    this.exprValidator = new ExprValidator(diags);
  }

  public Diagnostics diags() {
    return diags;
  }

  public Ast validate(Ast ast) {
    if (diags.hasError())
      throw new IllegalStateException("Validator used after errors detected");

    Ast result = new Ast();
    for (Stmt stmt : ast) {
      Stmt s = validateStmt(stmt);
      result.add(s);
      if (diags.hasError())
        break;
    }
    return result;
  }

  private boolean contextNotSet(Range range) {
    if (currentContext.isRoot()) {
      diags.fatal(range, "Only CONTEXT statements allowed at the root. Use `USE CONTEXT <name>;` first.");
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
      diags.error(uc.range(),
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
      diags.error(cd.range(), "Context '" + fqn + "' already defined at '" + currentContext.fullName() + "'");
    } else {
      catalog.put(currentContext, cd.context());
    }
    return cd;
  }

  private CreateType validateCreateType(CreateType ct) {
    if (contextNotSet(ct.range()))
      return ct;
    if (catalog.containsKey(currentContext, ct.qName())) {
      diags.error(ct.range(), "Type '" + ct.qName() + "' already defined at '" + currentContext.fullName() + "'");
      return ct;
    }
    ComplexT type = switch (ct.type()) {
      case EnumT tp -> validateEnum(tp);
      case StructT tp -> validateStruct(tp);
      case UnionT tp -> validateUnion(tp);
      case ScalarT tp -> validateScalar(tp);
    };
    if (diags.hasError())
      return ct;
    catalog.put(currentContext, type);
    return new CreateType(ct.range(), type);
  }

  private EnumT validateEnum(EnumT tp) {
    AstOptionalNode<IntegerT> enumBase = tp.type();
    EnumSymbolList converted = alignEnumSymbols(tp.symbols(), enumBase);
    if (diags.hasError())
      return tp;

    if (tp.defaultValue().isPresent()) {
      var enumName = tp.qName().name();
      var defaultEnumName = tp.defaultValue().get().enumName().name();
      if (!defaultEnumName.equals(enumName)) {
        diags.error(tp.defaultValue().get().range(),
            "Enum '" + tp.qName().fullName() + "' default symbol refers to wrong enum '"
                + defaultEnumName + "'");
      } else {
        var defaultSymbol = tp.defaultValue().get().symbol().name();
        boolean found = converted.stream().anyMatch(s -> s.name().name().equals(defaultSymbol));
        if (!found) {
          diags.error(tp.range(),
              "Enum '" + tp.qName().fullName() + "' default symbol '" + defaultSymbol
                  + "' is not one of the enum symbols");
        }
      }
    }
    if (diags.hasError())
      return tp;
    return new EnumT(tp.range(), tp.qName(), enumBase, converted, tp.defaultValue(), tp.doc());
  }

  private EnumSymbolList alignEnumSymbols(EnumSymbolList e, AstOptionalNode<IntegerT> type) {
    IntegerT base = new Int32T(Range.NONE);
    if (type.isPresent())
      base = type.get();
    EnumSymbolList converted = new EnumSymbolList(e.range());
    for (EnumSymbol sym : e) {
      if (diags.hasError())
        return converted;

      IntegerV v = exprValidator.alignIntegerValue(sym.value(), base);

      if (diags.hasError()) {
        diags.error(sym.range(),
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
      diags.error(tp.range(), "Struct '" + fqn.fullName() + "' must have at least one field");
      return tp;
    }
    var seen = new HashSet<String>();
    for (var field : tp.fieldList()) {
      if (!seen.add(field.name().name()))
        diags.error(field.range(),
            "Struct '" + tp.qName().fullName() + "' reuses field name '" + field.name().name() + "'");
    }
    if (diags.hasError())
      return tp;
    return tp;
  }

  private UnionT validateUnion(UnionT tp) {
    var fqn = tp.qName();
    if (tp.types().isEmpty()) {
      diags.error(tp.range(), "Union '" + fqn.fullName() + "' must have at least one option");
      return tp;
    }
    var seen = new HashSet<String>();
    for (var opt : tp.types()) {
      if (!seen.add(opt.name().name()))
        diags.error(opt.range(), "Union '" + fqn.fullName() + "' reuses option name '" + opt.name().name() + "'");
    }
    if (diags.hasError())
      return tp;
    return tp;
  }

  private ScalarT validateScalar(ScalarT tp) {
    QName fqn = tp.qName();
    PrimitiveT pt = tp.primitive();
    AstOptionalNode<CheckClause> vl = tp.checkClause();
    AstOptionalNode<PrimitiveV> dv = tp.defaultValue();
    if (catalog.containsKey(currentContext, fqn)) {
      diags.error(tp.range(), "Type '" + fqn + "' already defined");
      return tp;
    }
    if (dv.isPresent()) {
      dv = AstOptionalNode.of(exprValidator.alignPrimitive(dv.get(), pt));
      if (diags.hasError())
        return tp;
    }

    if (vl.isPresent()) {
      IntegerT ct = new Int32T(Range.NONE);
      Map<String, AnyT> symbols = new HashMap<>();
      symbols.put("value", pt);
      AnyT sexpr = exprValidator.trimExpression(vl.get().expr(), ct, symbols);
      if (diags.hasError())
        return tp;
    }

    return new ScalarT(tp.range(), fqn, pt, vl, dv, tp.doc());
  }

  private CreateStream validateStream(CreateStream cs) {
    if (contextNotSet(cs.range()))
      return null;
    var fqn = cs.qName();
    if (catalog.containsKey(currentContext, fqn)) {
      diags.error(cs.range(),
          "Stream '" + fqn.fullName() + "' already defined for context '" + currentContext.fullName() + "'");
      return cs;
    }
    var stream = cs.stream();
    var seen = new HashSet<String>();
    for (var alt : stream.types()) {
      if (diags.hasError())
        return cs;
      Identifier alias = alt.alias();
      Set<String> keySet = new HashSet<>();
      AstOptionalNode<DistributeClause> distributeClause = alt.distributeClause();
      AstListNode<Field> fields = null;
      if (!seen.add(alias.name()))
        diags.error(alt.range(), "Stream '" + fqn.fullName() + "' reuses alias '" + alias.name() + "'");
      if (alt instanceof StreamReferenceT ra) {
        var tname = ra.ref().qName();
        Optional<StructT> structOpt = catalog.getStruct(currentContext, tname);
        if (structOpt.isEmpty()) {
          diags.error(ra.range(),
              "Stream '" + fqn.fullName() + "' references unknown type '" + tname.fullName() + "'");
          continue;
        }
        fields = structOpt.get().fieldList();
      } else if (alt instanceof StreamInlineT it) {
        fields = it.fields();
      } else {
        diags.fatal(alt.range(), "Unexpected stream type definition");
        continue;
      }

      if (!distributeClause.isPresent())
        continue; // optional

      // Uniqueness
      for (Identifier k : distributeClause.get().keys()) {
        if (!keySet.add(k.name())) {
          diags.error(k.range(),
              "Duplicate field '" + k + "' in DISTRIBUTE clause for stream " +
                  stream.qName().fullName() + " type alias '" +
                  alias.name() + "'.");
        }
      }

      // Field existence
      keySet = fields.stream().map(f -> f.name().name()).collect(Collectors.toSet());

      for (Identifier k : distributeClause.get().keys()) {
        if (!keySet.contains(k.name())) {
          diags.error(k.range(),
              "Field '" + k + "' not found in type for DISTRIBUTE clause (stream " +
                  stream.qName().fullName() + ").");
        }
      }
    }
    var newStream = new StreamT(cs.range(), fqn, stream.types(), stream.doc());
    catalog.put(currentContext, newStream);
    return new CreateStream(cs.range(), newStream);
  }

  private ReadStmt validateReadStmt(ReadStmt rs) {
    Optional<StreamT> streamOpt = catalog.getStream(currentContext, rs.stream());
    if (streamOpt.isEmpty()) {
      diags.error(rs.range(), "Unknown stream '" + rs.stream().fullName() + "'");
      return rs;
    }
    StreamT stream = streamOpt.get();

    for (var block : rs.blocks()) {
      Set<Identifier> fields = topLevelFields(stream, block.alias(), catalog);
      if (fields == null) {
        diags.error(block.alias().range(),
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
        diags.fatal(block.projection().range(),
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
      diags.error(ws.range(), "Unknown stream '" + fqn.fullName() + "'");
      return ws;
    }
    StreamT ds = streamOpt.get();

    Set<Identifier> fields = topLevelFields(ds, ws.alias(), catalog);
    if (fields == null) {
      diags.error(ws.alias().range(),
          "Stream '" + fqn.fullName() + "': unknown TYPE alias '" + ws.alias().name() + "'");
      return ws;
    }

    return ws;
  }

  private Set<Identifier> topLevelFields(StreamT s, Identifier alias, NodeCatalog cat) {
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

  
}
