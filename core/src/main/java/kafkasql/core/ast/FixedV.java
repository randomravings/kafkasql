package kafkasql.core.ast;

public final record FixedV(Range range, byte[] value) implements BinaryV, Comparable<FixedV> {
    public int size() { return value.length; }
    public int compareTo(FixedV o) {
        for (int i = 0; i < Math.min(this.value.length, o.value.length); i++) {
            int cmp = Byte.compare(this.value[i], o.value[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(this.value.length, o.value.length);
    }
}
