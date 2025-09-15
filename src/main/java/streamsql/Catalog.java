package streamsql;

import java.util.*;
import java.util.stream.Collectors;

import streamsql.ast.*;
import streamsql.ast.EnumT;

public final class Catalog {
  private final Map<String, Object> table = new HashMap<>();
  
  public Catalog() {
    put(QName.root(), new Context(QName.root()));
  }

  public Boolean containsKey(QName fqn) { return table.containsKey(fqn.fullName()); }

  public Boolean put(Context ctx) { return put(ctx.qName(), ctx); }
  public Boolean put(StructT type) { return put(type.qName(), type); }
  public Boolean put(EnumT type) { return put(type.qName(), type); }
  public Boolean put(UnionT type) { return put(type.qName(), type); }
  public Boolean put(ScalarT type) { return put(type.qName(), type); }
  public Boolean put(DataStream stream) { return put(stream.qName(), stream); }

  private Boolean put(QName key, Object value) {
    if (table.containsKey(key.fullName())) return false;
    table.put(key.fullName(), value);
    return true;
  }

  public List<Object> all() { return new ArrayList<>(table.values()); }
  public List<ComplexT> allTypes() { return allT(ComplexT.class); }
  public List<Context> allContexts() { return allT(Context.class); }
  public List<StructT> allStructs() { return allT(StructT.class); }
  public List<EnumT> allEnums() { return allT(EnumT.class); }
  public List<UnionT> allUnions() { return allT(UnionT.class); }
  public List<ScalarT> allScalars() { return allT(ScalarT.class); }
  public List<DataStream> allStreams() { return allT(DataStream.class); }
  
  public Optional<Object> get(String fqn) { return Optional.ofNullable(table.get(fqn)); }
  public Optional<ComplexT> getType (QName fqn){ return getT(fqn, ComplexT.class); }
  public Optional<Context> getContext (QName fqn){ return getT(fqn, Context.class); }
  public Optional<StructT> getStruct (QName fqn){ return getT(fqn, StructT.class); }
  public Optional<EnumT> getEnum (QName fqn){ return getT(fqn, EnumT.class); }
  public Optional<UnionT> getUnion (QName fqn){ return getT(fqn, UnionT.class); }
  public Optional<ScalarT> getScalar (QName fqn){ return getT(fqn, ScalarT.class); }
  public Optional<DataStream> getStream (QName fqn){ return getT(fqn, DataStream.class); }

  private <T> Optional<T> getT(QName fqn, Class<T> clazz) {
    return get(fqn.fullName()).flatMap(val -> {
      if (clazz.isInstance(val)) {
        return Optional.of(clazz.cast(val));
      }
      return Optional.empty();
    });
  }

  public <T> List<T> allT(Class<T> clazz) {
    return table.values().stream()
      .filter(val -> clazz.isInstance(val))
      .map(val -> clazz.cast(val))
      .collect(Collectors.toList());
  }
}