package streamsql.ast;

public record Identifier(String value) implements Accessor, Comparable<Identifier> {
    @Override
    public int compareTo(Identifier o) {
        return value.compareTo(o.value);
    }
}
