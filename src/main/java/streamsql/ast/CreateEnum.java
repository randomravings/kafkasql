package streamsql.ast;

public final record CreateEnum(Enum type) implements CreateType {
    public QName qName() { return type.qName(); }
}
