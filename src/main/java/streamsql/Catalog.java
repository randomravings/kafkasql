package streamsql;

import java.util.*;
import java.util.stream.Collectors;

import streamsql.ast.*;
import streamsql.ast.Enum;

public final class Catalog {
  private final Map<String, Object> table = new HashMap<>();
  
  public Catalog() {
    put(QName.root(), new Context(QName.root()));
  }

  public Boolean containsKey(String fqn) { return table.containsKey(fqn); }

  public Boolean put(Context ctx) { return put(ctx.qName(), ctx); }
  public Boolean put(Struct type) { return put(type.qName(), type); }
  public Boolean put(Enum type) { return put(type.qName(), type); }
  public Boolean put(Union type) { return put(type.qName(), type); }
  public Boolean put(Scalar type) { return put(type.qName(), type); }
  public Boolean put(DataStream stream) { return put(stream.qName(), stream); }

  private Boolean put(QName key, Object value) {
    if (table.containsKey(key.fullName())) return false;
    table.put(key.fullName(), value);
    return true;
  }

  public List<Object> all() { return new ArrayList<>(table.values()); }
  public List<ComplexType> allTypes() { return allT(ComplexType.class); }
  public List<Context> allContexts() { return allT(Context.class); }
  public List<Struct> allStructs() { return allT(Struct.class); }
  public List<Enum> allEnums() { return allT(Enum.class); }
  public List<Union> allUnions() { return allT(Union.class); }
  public List<Scalar> allScalars() { return allT(Scalar.class); }
  public List<DataStream> allStreams() { return allT(DataStream.class); }
  
  public Optional<Object> get(String fqn) { return Optional.ofNullable(table.get(fqn)); }
  public Optional<ComplexType> getType (QName fqn){ return getT(fqn, ComplexType.class); }
  public Optional<Context> getContext (QName fqn){ return getT(fqn, Context.class); }
  public Optional<Struct> getStruct (QName fqn){ return getT(fqn, Struct.class); }
  public Optional<Enum> getEnum (QName fqn){ return getT(fqn, Enum.class); }
  public Optional<Union> getUnion (QName fqn){ return getT(fqn, Union.class); }
  public Optional<Scalar> getScalar (QName fqn){ return getT(fqn, Scalar.class); }
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