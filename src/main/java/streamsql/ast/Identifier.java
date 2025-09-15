package streamsql.ast;

public record Identifier(String value) implements Comparable<Identifier> {
    @Override
    public int compareTo(Identifier o) {
        return value.compareTo(o.value);
    }
}
