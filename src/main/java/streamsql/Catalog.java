package streamsql;

import java.util.*;
import java.util.stream.Collectors;

import streamsql.ast.*;

public final class Catalog {
  private final Map<String, CreateStmt> table = new HashMap<>();
  
  public Catalog() {
    put(new CreateContext(new Context(QName.root())));
  }

  public Boolean containsKey(String fqn) { return table.containsKey(fqn); }

  public Optional<CreateStmt> get(String fqn) { return Optional.ofNullable(table.get(fqn)); }

  public Boolean put(CreateStmt decl) {
    if (table.containsKey(decl.qName().fullName())) return false;
    table.put(decl.qName().fullName(), decl);
    return true;
  }

  public List<CreateStmt> all() { return new ArrayList<>(table.values()); }
  public List<ComplexType> allTypes() { return table.values().stream().filter(v -> v instanceof CreateType).map(v -> ((CreateType)v).type()).collect(Collectors.toList()); }
  public List<DataStream> allStreams() { return table.values().stream().filter(v -> v instanceof CreateStream).map(v -> ((CreateStream)v).stream()).collect(Collectors.toList()); }
  public List<Context> allContexts() { return table.values().stream().filter(v -> v instanceof CreateContext).map(v -> ((CreateContext)v).context()).collect(Collectors.toList()); }

  public Optional<Context> getCtx    (QName fqn){ return getT(fqn, CreateContext.class).flatMap(v -> Optional.of(v.context())); }
  public Optional<ComplexType> getType   (QName fqn){ return getT(fqn, CreateType.class).flatMap(v -> Optional.of(v.type())); }
  public Optional<Struct> getStruct (QName fqn){ return getType(fqn).flatMap(v -> { if(v instanceof Struct struct) return Optional.of(struct); return Optional.empty(); }); }
  public Optional<DataStream> getStream (QName fqn){ return getT(fqn, CreateStream.class).flatMap(v -> Optional.of(v.stream())); }

  private <T> Optional<T> getT(QName fqn, Class<T> clazz) {
    return get(fqn.fullName()).flatMap(val -> {
      if (clazz.isInstance(val)) {
        return Optional.of(clazz.cast(val));
      }
      return Optional.empty();
    });
  }
}