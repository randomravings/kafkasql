package streamsql;
import streamsql.ast.*;

import java.util.*;
import java.util.stream.Collectors;

public final class Validator {
  public Diagnostics validate(Catalog cat, Diagnostics diags, List<Stmt> stmts){
    var currentContext = QName.root();

    for (var st: stmts){
      switch (st) {
        case UseStmt uc:
          validateUseStmt(uc, currentContext, cat, diags);
          break;
        case CreateStmt od:
          validateCreateStmt(od, currentContext, cat, diags);
          break;
        case ReadStmt rq:
          validateReadStmt(rq, currentContext, cat, diags);
          break;
        case WriteStmt ws:
          validateWriteStmt(ws, currentContext, cat, diags);
          break;
        default:
          fatal(st, diags);
          break;
      }
      if (diags.hasFatal())
        return diags;
    }
    return diags;
  }

  private <T> void fatal(T type, Diagnostics diags) {
    diags.fatal("Unexpected statement type: " + type.getClass().getSimpleName());
  }

  private boolean ensureContextSet(QName cc, Diagnostics diags) {
    if (cc.isRoot()) {
      diags.error("Only CONTEXT statements allowed at the root. Use `USE CONTEXT <name>;` first.");
      return false;
    }
    return true;
  }

  private void validateUseStmt(UseStmt us, QName cc, Catalog cat, Diagnostics diags){
    switch (us) {
      case UseContext uc:
        validateUseContext(uc, cc, cat, diags);
        break;
      default:
        fatal(us, diags);
        break;
    }
  }

  private void validateCreateStmt(CreateStmt cs, QName cc, Catalog cat, Diagnostics diags){
    switch (cs) {
      case CreateContext cd:
        validateCreateContext(cd, cc, cat, diags);
        break;
      case CreateType td:
        validateTypeDecl(td, cc, cat, diags);
        break;
      case CreateStream as:
        validateAppendStreamDecl(as, cc, cat, diags);
        break;
      default:
        fatal(cs, diags);
        break;
    }
  }

  private void validateUseContext(UseContext uc, QName cc, Catalog cat, Diagnostics diags){
    Optional<Context> ctxOpt = cat.getCtx(uc.context().qName());
      if (ctxOpt.isPresent()) {
        cc = ctxOpt.get().qName();
      } else {
        diags.error("Unknown context '" + uc.context().qName() + "'");
    }
  }

  private void validateCreateContext(CreateContext cd, QName cc, Catalog cat, Diagnostics diags){
    Optional<Context> ctxOpt = cat.getCtx(cd.qName());
    if (ctxOpt.isPresent()) {
      cat.put(cd);
    } else {
      diags.error("Unknown context '" + cd.qName() + "'");
    }
  }

  private void validateTypeDecl(CreateType ct, QName cc, Catalog cat, Diagnostics diags){
    if (!ensureContextSet(cc, diags)) return;
    var fqn = ct.qName().fullName();
    if (cat.containsKey(fqn)) diags.error("Type '"+fqn+"' already defined");
    cat.put(ct);
  }

  private void validateAppendStreamDecl(CreateStream cs, QName cc, Catalog cat, Diagnostics diags){
    if (!ensureContextSet(cc, diags)) return;
    if (cat.containsKey(cs.qName().fullName())) diags.error("Stream '"+cs.qName().fullName()+"' already defined");
    var stream = cs.stream();
    var seen = new HashSet<Identifier>();
    for (var alt: stream.types()){
      Identifier alias = (alt instanceof StreamInlineT ia) ? ia.alias()
                     : ((StreamReferenceT)alt).alias();
      if (!seen.add(alias)) diags.error("Stream '"+cs.qName().fullName()+"' reuses alias '"+alias+"'");
      if (alt instanceof StreamReferenceT  ra) {
        var tname = ra.ref().qName().fullName();
        if (!cat.containsKey(tname)) diags.error("Stream '"+cs.qName().fullName()+"' references unknown type '"+tname+"'");
      }
    }
    cat.put(cs);
  }

  private void validateReadStmt(ReadStmt r, QName cc, Catalog cat, Diagnostics diags) {
    if (!ensureContextSet(cc, diags)) return;
    var streamOpt = cat.getStream(r.stream());
    if (streamOpt == null) {
        diags.error("Unknown stream '" + r.stream() + "'");
        return;
    }
    var stream = streamOpt.get();
    var aliases = new HashSet<Identifier>();
    for (var def : stream.types()) {
        if (def instanceof StreamInlineT ia) {
            aliases.add(ia.alias());
        } else if (def instanceof StreamReferenceT ra) {
            aliases.add(ra.alias());
        }
    }

    for (var block : r.blocks()) {
        var fields = topLevelFields(stream, block.alias(), cat);
        if (fields == null) {
            diags.error("Stream '" + r.stream() + "': unknown TYPE alias '" + block.alias() + "'");
            continue;
        }
        if (block.projection() instanceof ProjectionAll) continue;
        if (block.projection() instanceof ProjectionList col) {
            for (var p : col.fields()) {
                validatePath(p, fields, r, block, diags);
            }
        }
        else {
            diags.fatal("Unexpected projection type: " + block.projection().getClass().getSimpleName());
        }
    }
  }

  private void validatePath(Path p, Set<Identifier> fields, ReadStmt r, ReadSelection block, Diagnostics diags) {
    if (!fields.contains(p)) {
        diags.error("Stream '" + r.stream() + "' TYPE '" + block.alias() + "': unknown field '" + p.toString() + "' in path");
    }
  }

  private void validateWriteStmt(WriteStmt w, QName cc, Catalog cat, Diagnostics diags){
    if (!ensureContextSet(cc, diags)) return;
    var stream = cat.getStream(w.stream());
    if (stream==null){ diags.error("Unknown stream '"+w.stream()+"'"); return; }
    var fields = topLevelFields(stream.get(), w.alias(), cat);
    if (fields==null){ diags.error("Stream '"+w.stream()+"': unknown TYPE alias '"+w.alias()+"'"); return; }
    for (var p: w.projection()){
      if (!fields.contains(p.segments().getFirst()))
        diags.error("Stream '"+w.stream()+"' TYPE '"+w.alias()+"': unknown field '"+p.segments().getFirst()+"' in path");
      // TODO: deep path walk + type compatibility with row literals
    }
    for (int i=0;i<w.rows().size();i++){
      if (w.rows().get(i).values().size()!=w.projection().size())
        diags.error("WRITE row #"+(i+1)+": value count "+w.rows().get(i).values().size()+" != projection size "+w.projection().size());
    }
  }

  private Set<Identifier> topLevelFields(DataStream s, Identifier alias, Catalog cat){
    StreamType alt = s.types().stream().filter(t -> t.alias().equals(alias)).findFirst().orElse(null);
    if (alt instanceof StreamInlineT ia) return ia.fields().stream().map(Field::name).collect(Collectors.toSet());
    if (alt instanceof StreamReferenceT ra) {
      var td = cat.getStruct(ra.ref().qName());
      if (td==null) return null;
      return td.get().fields().stream().map(Field::name).collect(Collectors.toSet());
    }
    return null;
  }
}
