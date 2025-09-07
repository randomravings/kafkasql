package streamsql.ast;

public final class BytesV implements Chars<BytesV, byte[]> {
    private final byte[] value;
    public BytesV(byte[] value) { this.value = value; }
    public byte[] value() { return value; }
    public BytesV concat(BytesV other) {
        byte[] newValue = new byte[this.value.length + other.value.length];
        System.arraycopy(this.value, 0, newValue, 0, this.value.length);
        System.arraycopy(other.value, 0, newValue, this.value.length, other.value.length);
        return new BytesV(newValue);
    }
}
