package streamsql.ast;

@SuppressWarnings("unchecked")
public final record FixedV(byte[] value) implements BinaryV {
    public Int32V length() { return new Int32V(value.length); }
    public FixedV concat(FixedV other) {
        byte[] newValue = new byte[this.value.length + other.value.length];
        System.arraycopy(this.value, 0, newValue, 0, this.value.length);
        System.arraycopy(other.value, 0, newValue, this.value.length, other.value.length);
        return new FixedV(newValue);
    }
}
