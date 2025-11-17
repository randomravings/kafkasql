package kafkasql.lang;

import java.util.*;
import java.util.stream.Collectors;

import kafkasql.lang.ast.*;

public final class NodeCatalog {
  private final Map<String, Object> table = new HashMap<>();
  
  public NodeCatalog() {
    table.put(Context.ROOT.qName().fullName(), Context.ROOT.qName());
  }

  private static String resolveName(QName ctx, QName fqn) {
    if (fqn.dotPrefix().isPresent()) return fqn.fullName();
    return ctx.isRoot() ? fqn.fullName() : ctx.fullName() + "." + fqn.fullName();
  }

  public Boolean containsKey(QName ctx, QName fqn) {
    var name = resolveName(ctx, fqn);
    return table.containsKey(name);
  }

  public Boolean put(QName curContext, Context ctx) { return put(curContext, ctx.qName(), ctx); }
  public Boolean put(QName curContext, StructT type) { return put(curContext, type.qName(), type); }
  public Boolean put(QName curContext, EnumT type) { return put(curContext, type.qName(), type); }
  public Boolean put(QName curContext, UnionT type) { return put(curContext, type.qName(), type); }
  public Boolean put(QName curContext, ScalarT type) { return put(curContext, type.qName(), type); }
  public Boolean put(QName curContext, StreamT stream) { return put(curContext, stream.qName(), stream); }
  public Boolean put(QName curContext, ComplexT type) { return put(curContext, type.qName(), type); }

  private Boolean put(QName ctx, QName key, Object value) {
    var name = resolveName(ctx, key);
    if (table.containsKey(name)) return false;
    table.put(name, value);
    return true;
  }

  public List<Object> all() { return new ArrayList<>(table.values()); }
  public List<ComplexT> allTypes() { return allT(ComplexT.class); }
  public List<Context> allContexts() { return allT(Context.class); }
  public List<StructT> allStructs() { return allT(StructT.class); }
  public List<EnumT> allEnums() { return allT(EnumT.class); }
  public List<UnionT> allUnions() { return allT(UnionT.class); }
  public List<ScalarT> allScalars() { return allT(ScalarT.class); }
  public List<StreamT> allStreams() { return allT(StreamT.class); }

  public Optional<Object> get(QName curContext, QName fqn) { return getT(curContext, fqn, Object.class); }
  public Optional<ComplexT> getType (QName curContext, QName fqn){ return getT(curContext, fqn, ComplexT.class); }
  public Optional<Context> getContext (QName curContext, QName fqn){ return getT(curContext, fqn, Context.class); }
  public Optional<StructT> getStruct (QName curContext, QName fqn){ return getT(curContext, fqn, StructT.class); }
  public Optional<EnumT> getEnum (QName curContext, QName fqn){ return getT(curContext, fqn, EnumT.class); }
  public Optional<UnionT> getUnion (QName curContext, QName fqn){ return getT(curContext, fqn, UnionT.class); }
  public Optional<ScalarT> getScalar (QName curContext, QName fqn){ return getT(curContext, fqn, ScalarT.class); }
  public Optional<StreamT> getStream (QName curContext, QName fqn){ return getT(curContext, fqn, StreamT.class); }

  private <T> Optional<T> getT(QName curContext, QName fqn, Class<T> clazz) {
    var name = resolveName(curContext, fqn);
    var value = table.get(name);
    if(value == null) return Optional.empty();
    if(!clazz.isInstance(value)) return Optional.empty();
    return Optional.of(clazz.cast(value));
  }

  public <T> List<T> allT(Class<T> clazz) {
    return table.values().stream()
      .filter(val -> clazz.isInstance(val))
      .map(val -> clazz.cast(val))
      .collect(Collectors.toList());
  }
}