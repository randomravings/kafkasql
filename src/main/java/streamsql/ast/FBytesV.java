package streamsql.ast;

public final class FBytesV implements Chars<FBytesV, byte[]> {
    private final byte[] value;
    private final int length;
    public FBytesV(byte[] value, int length) {
        this.value = value;
        if (value.length > length)
            throw new IllegalArgumentException("FBytes length exceeds defined limit: " + length);
        this.length = length;
    }
    public byte[] value() { return value; }
    public int length() { return length; }
    public FBytesV concat(FBytesV other) {
        byte[] newValue = new byte[this.value.length + other.value.length];
        System.arraycopy(this.value, 0, newValue, 0, this.value.length);
        System.arraycopy(other.value, 0, newValue, this.value.length, other.value.length);
        return new FBytesV(newValue, this.length + other.length());
    }
}
