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
        case Create od:
          validateCreateStmt(od, currentContext, cat, diags);
          break;
        case Dml.Read rq:
          validateReadStmt(rq, currentContext, cat, diags);
          break;
        case Dml.Write ws:
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

  private void validateCreateStmt(Create cs, QName cc, Catalog cat, Diagnostics diags){
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
    Optional<Context> ctxOpt = cat.getCtx(uc.context().qName().fullName());
      if (ctxOpt.isPresent()) {
        cc = ctxOpt.get().qName();
      } else {
        diags.error("Unknown context '" + uc.context().qName().fullName() + "'");
    }
  }

  private void validateCreateContext(CreateContext cd, QName cc, Catalog cat, Diagnostics diags){
    Optional<Context> ctxOpt = cat.getCtx(cd.qName().fullName());
    if (ctxOpt.isPresent()) {
      cat.put(cd);
    } else {
      diags.error("Unknown context '" + cd.qName().fullName() + "'");
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
    var seen = new HashSet<String>();
    for (var alt: stream.types()){
      String alias = (alt instanceof Stream.InlineType ia) ? ia.alias()
                     : ((Stream.ReferenceType)alt).alias();
      if (!seen.add(alias)) diags.error("Stream '"+cs.qName().fullName()+"' reuses alias '"+alias+"'");
      if (alt instanceof Stream.ReferenceType  ra) {
        var tname = ra.ref().qName().fullName();
        if (!cat.containsKey(tname)) diags.error("Stream '"+cs.qName().fullName()+"' references unknown type '"+tname+"'");
      }
    }
    cat.put(cs);
  }

  private void validateReadStmt(Dml.Read r, QName cc, Catalog cat, Diagnostics diags){
    if (!ensureContextSet(cc, diags)) return;
    var stream = cat.getStream(r.stream);
    if (stream==null){ diags.error("Unknown stream '"+r.stream+"'"); return; }
    var aliases = new HashSet<String>();
    for (var a: stream.get().types())
      aliases.add(a instanceof Stream.InlineType ia ? ia.alias() : ((Stream.ReferenceType)a).alias());

    for (var b: r.blocks){
      if (!aliases.contains(b.typeName()))
        diags.error("Stream '"+r.stream+"': unknown TYPE alias '"+b.typeName()+"'");
      var fields = topLevelFields(stream.get(), b.typeName(), cat);
      for (var sel: b.select()){
        if (sel instanceof Dml.Read.Star) continue;
        var col = (Dml.Read.Col)sel;
        var head = col.name().parts().isEmpty()? null : col.name().parts().get(0);
        if (fields==null || head==null || !fields.contains(head))
          diags.error("Stream '"+r.stream+"' TYPE '"+b.typeName()+"': unknown field '"+String.join(".", col.name().parts())+"'");
      }
    }
  }

  private void validateWriteStmt(Dml.Write w, QName cc, Catalog cat, Diagnostics diags){
    if (!ensureContextSet(cc, diags)) return;
    var stream = cat.getStream(w.stream);
    if (stream==null){ diags.error("Unknown stream '"+w.stream+"'"); return; }
    var fields = topLevelFields(stream.get(), w.typeName, cat);
    if (fields==null){ diags.error("Stream '"+w.stream+"': unknown TYPE alias '"+w.typeName+"'"); return; }
    for (var p: w.projection){
      if (!fields.contains(p.head()))
        diags.error("Stream '"+w.stream+"' TYPE '"+w.typeName+"': unknown field '"+p.head()+"' in path");
      // TODO: deep path walk + type compatibility with row literals
    }
    for (int i=0;i<w.rows.size();i++){
      if (w.rows.get(i).size()!=w.projection.size())
        diags.error("WRITE row #"+(i+1)+": value count "+w.rows.get(i).size()+" != projection size "+w.projection.size());
    }
  }

  private Set<String> topLevelFields(StreamType s, String alias, Catalog cat){
    StreamType.Definition alt = s.types().stream().filter(t -> t.alias().equals(alias)).findFirst().orElse(null);
    if (alt instanceof Stream.InlineType ia) return ia.fields().stream().map(Complex.StructField::name).collect(Collectors.toSet());
    if (alt instanceof Stream.ReferenceType ra) {
      var td = cat.getStruct(ra.ref().qName().fullName());
      if (td==null) return null;
      return td.get().fields().stream().map(Complex.StructField::name).collect(Collectors.toSet());
    }
    return null;
  }
}
