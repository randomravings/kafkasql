package streamsql.ast;

public final record CreateEnum(EnumT type) implements CreateType {
    public QName qName() { return type.qName(); }
}
