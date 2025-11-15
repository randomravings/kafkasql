package kafkasql.core.ast;

import java.util.UUID;

public final record UuidV(Range range, UUID value) implements AlphaV, Comparable<UuidV> {
    @Override
    public int compareTo(UuidV o) {
        return this.value.compareTo(o.value);
    }
}
