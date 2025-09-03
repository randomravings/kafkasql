package streamsql.ast;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class QName {

  private static final QName ROOT = new QName(List.of());
  private final List<String> parts;

  private QName(List<String> parts) {
    this.parts = Collections.unmodifiableList(parts);
  }

  public Boolean isRoot() {
    return parts.isEmpty();
  }

  public List<String> parts() {
    return parts;
  }

  public String context() {
    return parts.size() > 1 ? String.join(".", parts.subList(0, parts.size() - 1)) : "";
  }

  public String name() {
    return parts.size() > 0 ? parts.getLast() : "";
  }

  public String fullName() {
    return String.join(".", parts);
  }

  public QName append(String part) {
    var newParts = Stream.concat(parts.stream(), Stream.of(part)).toList();
    return new QName(newParts);
  }

  public QName appendAll(List<String> more) {
    if (more.isEmpty()) return this;
    var newParts = Stream.concat(parts.stream(), more.stream()).toList();
    return new QName(newParts);
  }

  public static QName root() { return ROOT; }
  public static QName of(String... parts) { return new QName(List.of(parts)); }
  public static QName of(List<String> parts) { return new QName(parts); }
  public static QName of(String parts) { return new QName(List.of(parts.split("\\."))); }
  public static QName join(QName a, QName b) { return new QName(Stream.concat(a.parts.stream(), b.parts.stream()).toList()); }
}