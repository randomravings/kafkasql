package streamsql.ast;

@SuppressWarnings("unchecked")
public final record BytesV(byte[] value) implements BinaryV {
    public BytesV concat(BytesV other) {
        byte[] newValue = new byte[this.value.length + other.value.length];
        System.arraycopy(this.value, 0, newValue, 0, this.value.length);
        System.arraycopy(other.value, 0, newValue, this.value.length, other.value.length);
        return new BytesV(newValue);
    }
}
