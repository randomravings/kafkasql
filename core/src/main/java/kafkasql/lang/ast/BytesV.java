package kafkasql.lang.ast;

public final record BytesV(Range range, byte[] value) implements BinaryV, Comparable<BytesV> {
    @Override
    public int compareTo(BytesV o) {
        for (int i = 0; i < Math.min(this.value.length, o.value.length); i++) {
            int cmp = Byte.compare(this.value[i], o.value[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(this.value.length, o.value.length);
    }
}
