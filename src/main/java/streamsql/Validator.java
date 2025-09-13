package streamsql;
import streamsql.ast.*;

import java.util.*;
import java.util.stream.Collectors;

public final class Validator {
  public final Catalog catalog;
  private final Diagnostics  diags = new Diagnostics();
  private QName currentContext = QName.root();
  private List<Stmt> stmts = new ArrayList<>();

  public Validator(Catalog catalog) {
    this.catalog = catalog;
  }

  public Validator(Catalog catalog, QName initialContext) {
    this.catalog = catalog;
    this.currentContext = initialContext;
  }

  public ParseResult validate(List<Stmt> tree){
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
  private UseStmt validateUseStmt(UseStmt us){
    return switch (us) {
      case UseContext uc -> validateUseContext(uc);
      default -> us;
    };
  }

  private UseContext validateUseContext(UseContext uc){
    Optional<Context> ctx = catalog.getContext(uc.context().qName());
    if (!ctx.isPresent()) {
      diags.error("Unknown context '" + uc.context().qName().fullName() + "'");
    }
    else {
      currentContext = uc.context().qName();
    }
    return uc;
  }

  private CreateStmt validateCreateStmt(CreateStmt cs){
    return switch (cs) {
      case CreateContext cd -> validateCreateContext(cd);
      case CreateType td -> validateCreateType(td);
      case CreateStream as -> validateAppendStreamDecl(as);
      default -> cs;
    };
  }

  private CreateContext validateCreateContext(CreateContext cd){
    var fqn = cd.qName().fullName();
    if (catalog.containsKey(fqn)) {
      diags.error("Context '" + fqn + "' already defined");
    } else {
      catalog.put(cd.context());
    }
    return cd;
  }

  // Now returns Optional<CreateType> so a normalized CreateEnum can be swapped into the statement list
  private CreateType validateCreateType(CreateType ct){
    if (contextNotSet()) return ct;
    var fqn = ct.qName().fullName();
    if (catalog.containsKey(fqn)) {
      diags.error("Type '"+fqn+"' already defined");
      return ct;
    }
    switch (ct) {
      case CreateEnum ce:
        return validateCreateEnum(ce);
      case CreateStruct cs:
        catalog.put(cs.type());
        return cs;
      case CreateUnion cu:
        catalog.put(cu.type());
        return cu;
      case CreateScalar cs:
        catalog.put(cs.type());
        return cs;
      default:
        return ct;
    }
  }

  private CreateEnum validateCreateEnum(CreateEnum ce){
      streamsql.ast.Enum e = ce.type();
      IntegerT enumBase = e.type();

      List<EnumSymbol> converted =  alignEnumSymbols(e.symbols(), enumBase);
      if (diags.hasErrors()) return ce;

      // validate default symbol (if present) refers to one of the defined symbols
      if (e.defaultSymbol().isPresent()) {
        Identifier def = e.defaultSymbol().get();
        boolean found = converted.stream().anyMatch(s -> s.name().equals(def));
        if (!found) {
          diags.error("Enum '" + e.qName().fullName() + "' default symbol '" + def.value() + "' is not one of the enum symbols");
        }
        if (diags.hasErrors()) return ce;
      }
      var ne = new CreateEnum(new streamsql.ast.Enum(e.qName(), enumBase, e.isMask(), converted, e.defaultSymbol()));
      catalog.put(ne.type());
      return ne;
  }

  private List<EnumSymbol> alignEnumSymbols(List<EnumSymbol> e, IntegerT type) {
    List<EnumSymbol> converted = new ArrayList<>();
    for (EnumSymbol sym : e) {
      if (diags.hasErrors())
        return converted;

      long v = sym.value().value();
      if(type instanceof Int8T) {
        if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
          diags.error("Enum symbol value for '" + sym.name().value() + "' does not fit in INT8");
          continue;
        }
        converted.add(new EnumSymbol(sym.name(), new Int8V((byte)v)));
      }
      else if(type instanceof Int16T) {
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
          diags.error("Enum symbol value for '" + sym.name().value() + "' does not fit in INT16");
          continue;
        }
        converted.add(new EnumSymbol(sym.name(), new Int16V((short)v)));
      }
      else if(type instanceof Int32T) {
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
          diags.error("Enum symbol value for '" + sym.name().value() + "' does not fit in INT32");
          continue;
        }
        converted.add(new EnumSymbol(sym.name(), new Int32V((int)v)));
      }
      else if(type instanceof Int64T) {
        converted.add(sym);
      }
      else {
        diags.error("Enum symbol value for '" + sym.name().value() + "' has unsupported underlying integer type");
        continue;
      }
    }
    return converted;
  }

  private CreateStream validateAppendStreamDecl(CreateStream cs){
    if (contextNotSet()) return null;
    var fqn = cs.qName().fullName();
    if (catalog.containsKey(fqn)) {
      diags.error("Stream '"+fqn+"' already defined");
      return cs;
    }
    var stream = cs.stream();
    var seen = new HashSet<String>();
    for (var alt: stream.types()){
      Identifier alias = alt.alias();
      if (!seen.add(alias.value())) diags.error("Stream '"+fqn+"' reuses alias '"+alias.value()+"'");
      if (alt instanceof StreamReferenceT  ra) {
        var tname = ra.ref().qName().fullName();
        if (!catalog.containsKey(tname)) diags.error("Stream '"+fqn+"' references unknown type '"+tname+"'");
      }
    }
    catalog.put(cs.stream());
    return cs;
  }

  private ReadStmt validateReadStmt(ReadStmt rs) {
    if (contextNotSet()) return null;
    var fqn = rs.stream();
    if(fqn.parts().size() == 1) {
      fqn = currentContext.append(fqn.parts().get(0));
    }
    Optional<DataStream> streamOpt = catalog.getStream(fqn);
    if (streamOpt.isEmpty()) {
        diags.error("Unknown stream '" + fqn.fullName() + "'");
        return rs;
    }
    DataStream stream = streamOpt.get();

    for (var block : rs.blocks()) {
        Set<Identifier> fields = topLevelFields(stream, block.alias(),  catalog);
        if (fields == null) {
            diags.error("Stream '" + rs.stream().fullName() + "': unknown TYPE alias '" + block.alias().value() + "'");
            continue;
        }
        if (block.projection() instanceof ProjectionAll) continue;
        if (block.projection() instanceof ProjectionList col) {
            for (Accessor p : col.fields()) {
                validatePath(p, fields, rs, block, diags);
            }
        } else {
            diags.fatal("Unexpected projection type: " + block.projection().getClass().getSimpleName());
        }
    }

    return rs;
  }

  // validate only checks top-level head exists; deeper validation deferred to semantic/type checking
  private void validatePath(Accessor p, Set<Identifier> fields, ReadStmt r, ReadSelection block, Diagnostics diags) {
    Identifier head = null;
    if (p instanceof Segment s) {
      Accessor h = s.head();
      if (h instanceof Identifier id) head = id;
    } else if (p instanceof Identifier id) {
      head = id;
    } else {
      diags.error("Stream '" + r.stream().fullName() + "' TYPE '" + block.alias().value() + "': access path must start with an identifier");
      return;
    }

    if (head == null) {
      diags.error("Stream '" + r.stream().fullName() + "' TYPE '" + block.alias().value() + "': invalid access path head");
      return;
    }

    if (!fields.contains(head)) {
        diags.error("Stream '" + r.stream().fullName() + "' TYPE '" + block.alias().value() + "': unknown field '" + head.value() + "' in path");
    }
  }

  private WriteStmt validateWriteStmt(WriteStmt ws) {
    if (contextNotSet()) return null;
    var fqn = ws.stream();
    if(fqn.parts().size() == 1) {
      fqn = currentContext.append(fqn.parts().get(0));
    }
    Optional<DataStream> streamOpt = catalog.getStream(fqn);
    if (streamOpt.isEmpty()){ diags.error("Unknown stream '"+fqn.fullName()+"'"); return ws; }
    DataStream ds = streamOpt.get();

    Set<Identifier> fields = topLevelFields(ds, ws.alias(), catalog);
    if (fields==null){ diags.error("Stream '"+fqn.fullName()+"': unknown TYPE alias '"+ws.alias().value()+"'"); return ws; }

    if (ws.projection() instanceof ProjectionList col) {
        for (Accessor p : col.fields()) {
            validatePath(p, fields, ws, diags);
        }
    } else {
        diags.fatal("Unexpected projection type: " + ws.projection().getClass().getSimpleName());
    }

    int projSize = 0;
    if (ws.projection() instanceof ProjectionList pl) projSize = pl.fields().size();

    for (int i=0;i<ws.rows().size();i++){
      if (ws.rows().get(i).values().size()!=projSize)
        diags.error("WRITE row #"+(i+1)+": value count "+ws.rows().get(i).values().size()+" != projection size "+projSize);
    }

    return ws;
  }

  private void validatePath(Accessor p, Set<Identifier> fields, WriteStmt r, Diagnostics diags) {
    Identifier head = null;
    if (p instanceof Segment s) {
      Accessor h = s.head();
      if (h instanceof Identifier id) head = id;
    } else if (p instanceof Identifier id) {
      head = id;
    } else {
      diags.error("Stream '" + r.stream().fullName() + "' TYPE '" + r.alias().value() + "': access path must start with an identifier");
      return;
    }

    if (head == null) {
      diags.error("Stream '" + r.stream().fullName() + "' TYPE '" + r.alias().value() + "': invalid access path head");
      return;
    }

    if (!fields.contains(head)) {
        diags.error("Stream '" + r.stream().fullName() + "' TYPE '" + r.alias().value() + "': unknown field '" + head.value() + "' in path");
    }
  }

  private Set<Identifier> topLevelFields(DataStream s, Identifier alias, Catalog cat){
    StreamType alt = s.types().stream().filter(t -> t.alias().equals(alias)).findFirst().orElse(null);
    if (alt instanceof StreamInlineT ia) return ia.fields().stream().map(Field::name).collect(Collectors.toSet());
    if (alt instanceof StreamReferenceT ra) {
      Optional<Struct> td = cat.getStruct(ra.ref().qName());
      if (td.isEmpty()) return null;
      return td.get().fields().stream().map(Field::name).collect(Collectors.toSet());
    }
    return null;
  }
}
